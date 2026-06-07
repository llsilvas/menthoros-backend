# Strava - Guia Completo de Integração

> **Nota**: Este documento foi consolidado a partir dos arquivos `STRAVA_INTEGRATION_GUIDE.md` e `STRAVA_IMPLEMENTATION_ROADMAP.md`, integrando o guia de implementação com o roadmap de desenvolvimento.

---

## 📋 Índice

1. [Visão Geral](#visão-geral)
2. [Arquitetura da Integração](#arquitetura-da-integração)
3. [Credenciais do App Strava](#credenciais-do-app-strava)
4. [Implementação Passo a Passo](#implementação-passo-a-passo)
5. [Estrutura de Dados](#estrutura-de-dados)
6. [Fluxo de Autenticação OAuth2](#fluxo-de-autenticação-oauth2)
7. [Sincronização de Atividades](#sincronização-de-atividades)
8. [Webhooks do Strava](#webhooks-do-strava)
9. [Mapeamento de Dados](#mapeamento-de-dados)
10. [Testes e Validação](#testes-e-validação)
11. [Segurança e Boas Práticas](#segurança-e-boas-práticas)
12. [Roadmap de Implementação](#roadmap-de-implementação)
13. [Checklist Final](#checklist-final)

---

## 📖 Visão Geral

A integração com o Strava permite que o Menthoros:
- ✅ **Importe automaticamente** treinos realizados do Strava
- ✅ **Sincronize dados** em tempo real via webhooks
- ✅ **Enriqueça métricas** com dados precisos de GPS, frequência cardíaca e pace
- ✅ **Compare** treinos planejados vs realizados
- ✅ **Calcule TSS** baseado em dados reais do Strava

---

## 🏗️ Arquitetura da Integração

```
┌─────────────────┐
│   Strava API    │
│   (OAuth 2.0)   │
└────────┬────────┘
         │
         │ 1. Autorização
         │ 2. Token Exchange
         │ 3. Refresh Token
         ▼
┌─────────────────────────────────────────┐
│       Menthoros Backend (Spring)        │
│  ┌───────────────────────────────────┐  │
│  │  StravaAuthController             │  │
│  │  - /strava/auth                   │  │
│  │  - /strava/callback               │  │
│  └────────────┬──────────────────────┘  │
│               │                          │
│  ┌────────────▼──────────────────────┐  │
│  │  StravaOAuthService               │  │
│  │  - exchangeCodeForToken()         │  │
│  │  - refreshAccessToken()           │  │
│  └────────────┬──────────────────────┘  │
│               │                          │
│  ┌────────────▼──────────────────────┐  │
│  │  StravaActivityService            │  │
│  │  - syncActivities()               │  │
│  │  - importActivity()               │  │
│  │  - mapToTreinoRealizado()         │  │
│  └────────────┬──────────────────────┘  │
│               │                          │
│  ┌────────────▼──────────────────────┐  │
│  │  StravaWebhookController          │  │
│  │  - /strava/webhook (GET/POST)     │  │
│  └────────────┬──────────────────────┘  │
│               │                          │
│  ┌────────────▼──────────────────────┐  │
│  │  Database (PostgreSQL)            │  │
│  │  - tb_strava_auth                 │  │
│  │  - tb_treino_realizado            │  │
│  │  - tb_atleta                      │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

---

## 🔑 Credenciais do App Strava

### Informações Necessárias

Após criar seu app no Strava ([https://www.strava.com/settings/api](https://www.strava.com/settings/api)), você recebeu:

| Campo | Descrição | Exemplo |
|-------|-----------|---------|
| **Client ID** | ID público do seu aplicativo | `123456` |
| **Client Secret** | Chave secreta (NUNCA exponha!) | `abc123def456...` |
| **Authorization Callback Domain** | Domínio autorizado | `localhost` ou `app.menthoros.com` |

### Configuração no application.yml

```yaml
# application.yml
app:
  strava:
    client-id: ${STRAVA_CLIENT_ID}
    client-secret: ${STRAVA_CLIENT_SECRET}
    redirect-uri: ${STRAVA_REDIRECT_URI:http://localhost:8098/api/strava/callback}
    authorization-uri: https://www.strava.com/oauth/authorize
    token-uri: https://www.strava.com/oauth/token
    api-base-url: https://www.strava.com/api/v3
    webhook-verify-token: ${STRAVA_WEBHOOK_TOKEN:menthoros_webhook_secret}
```

### Variáveis de Ambiente (.env)

```bash
# .env
STRAVA_CLIENT_ID=123456
STRAVA_CLIENT_SECRET=abc123def456ghi789
STRAVA_REDIRECT_URI=http://localhost:8098/api/strava/callback
STRAVA_WEBHOOK_TOKEN=menthoros_webhook_secret_2024
```

---

## 🛠️ Implementação Passo a Passo

### **ETAPA 1: Adicionar Dependências**

#### pom.xml
```xml
<!-- Adicionar ao pom.xml -->
<dependencies>
    <!-- Spring Security OAuth2 Client -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-client</artifactId>
    </dependency>

    <!-- WebClient para chamadas HTTP assíncronas -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>

    <!-- JSON Processing -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
</dependencies>
```

---

### **ETAPA 2: Criar Entidade de Autenticação Strava**

#### StravaAuth.java
```java
package com.menthoros.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_strava_auth",
    indexes = @Index(name = "idx_strava_atleta", columnList = "atleta_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StravaAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atleta_id", unique = true, nullable = false)
    private Atleta atleta;

    @Column(name = "strava_athlete_id", unique = true, nullable = false)
    private Long stravaAthleteId;

    @Column(name = "access_token", nullable = false, length = 512)
    private String accessToken;

    @Column(name = "refresh_token", nullable = false, length = 512)
    private String refreshToken;

    @Column(name = "token_expires_at", nullable = false)
    private LocalDateTime tokenExpiresAt;

    @Column(name = "scope", length = 255)
    private String scope; // Ex: "read,activity:read_all"

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Verifica se o token está expirado ou expira nos próximos 5 minutos
     */
    public boolean isTokenExpired() {
        return tokenExpiresAt.isBefore(LocalDateTime.now().plusMinutes(5));
    }
}
```

---

### **ETAPA 3: Migration do Banco de Dados**

#### V7__Create_strava_auth_table.sql
```sql
-- src/main/resources/db/migration/V7__Create_strava_auth_table.sql

CREATE TABLE tb_strava_auth (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    atleta_id UUID NOT NULL UNIQUE REFERENCES tb_atleta(id) ON DELETE CASCADE,
    strava_athlete_id BIGINT NOT NULL UNIQUE,
    access_token VARCHAR(512) NOT NULL,
    refresh_token VARCHAR(512) NOT NULL,
    token_expires_at TIMESTAMP NOT NULL,
    scope VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    last_sync_at TIMESTAMP
);

CREATE INDEX idx_strava_atleta ON tb_strava_auth(atleta_id);
CREATE INDEX idx_strava_athlete_id ON tb_strava_auth(strava_athlete_id);

-- Adicionar campo external_id na tabela de treinos (se ainda não existir)
ALTER TABLE tb_treino_realizado
ADD COLUMN IF NOT EXISTS external_id VARCHAR(255),
ADD COLUMN IF NOT EXISTS fonte_dados VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_treino_external_id ON tb_treino_realizado(external_id);
```

---

### **ETAPA 4: DTOs de Comunicação com Strava**

#### StravaTokenResponse.java
```java
package com.menthoros.dto.strava;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class StravaTokenResponse {

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_at")
    private Long expiresAt; // Unix timestamp

    @JsonProperty("expires_in")
    private Integer expiresIn; // Seconds

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("athlete")
    private StravaAthleteDto athlete;
}

@Data
class StravaAthleteDto {
    private Long id;
    private String username;
    private String firstname;
    private String lastname;
    private String city;
    private String state;
    private String country;
    private String sex; // M, F
    private String profile; // URL da foto
}
```

#### StravaActivityDto.java
```java
package com.menthoros.dto.strava;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.ZonedDateTime;
import java.util.List;

@Data
public class StravaActivityDto {

    private Long id;
    private String name;
    private String type; // "Run", "Ride", etc.

    @JsonProperty("start_date")
    private ZonedDateTime startDate;

    @JsonProperty("start_date_local")
    private ZonedDateTime startDateLocal;

    private String timezone;

    // Distância em metros
    private Double distance;

    // Duração em segundos
    @JsonProperty("moving_time")
    private Integer movingTime;

    @JsonProperty("elapsed_time")
    private Integer elapsedTime;

    // Elevação em metros
    @JsonProperty("total_elevation_gain")
    private Double totalElevationGain;

    // Velocidade em m/s
    @JsonProperty("average_speed")
    private Double averageSpeed;

    @JsonProperty("max_speed")
    private Double maxSpeed;

    // Frequência cardíaca
    @JsonProperty("average_heartrate")
    private Double averageHeartrate;

    @JsonProperty("max_heartrate")
    private Double maxHeartrate;

    @JsonProperty("has_heartrate")
    private Boolean hasHeartrate;

    @JsonProperty("suffer_score")
    private Integer sufferScore; // Similar ao TSS

    private Double calories;

    @JsonProperty("perceived_exertion")
    private Integer perceivedExertion; // RPE

    private String description;

    @JsonProperty("manual")
    private Boolean manual;

    @JsonProperty("workout_type")
    private Integer workoutType; // 0=default, 1=race, 2=long run, 3=workout

    @JsonProperty("splits_metric")
    private List<StravaSplitDto> splitsMetric;
}

@Data
class StravaSplitDto {
    private Double distance; // metros
    @JsonProperty("elapsed_time")
    private Integer elapsedTime;
    @JsonProperty("elevation_difference")
    private Double elevationDifference;
    @JsonProperty("moving_time")
    private Integer movingTime;
    @JsonProperty("average_speed")
    private Double averageSpeed;
    @JsonProperty("average_heartrate")
    private Double averageHeartrate;
}
```

---

### **ETAPA 5: Configuração Properties**

#### StravaProperties.java
```java
package com.menthoros.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.strava")
public class StravaProperties {

    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String authorizationUri;
    private String tokenUri;
    private String apiBaseUrl;
    private String webhookVerifyToken;

    /**
     * Scopes necessários:
     * - read: Leitura de dados básicos
     * - activity:read_all: Leitura de todas as atividades
     * - activity:write: Criação de atividades (futuro)
     */
    public String getDefaultScopes() {
        return "read,activity:read_all";
    }
}
```

---

### **ETAPA 6: Service de Autenticação OAuth2**

#### StravaOAuthService.java
```java
package com.menthoros.services;

import com.menthoros.config.StravaProperties;
import com.menthoros.dto.strava.StravaTokenResponse;
import com.menthoros.entity.Atleta;
import com.menthoros.entity.StravaAuth;
import com.menthoros.repository.StravaAuthRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StravaOAuthService {

    private final StravaProperties stravaProperties;
    private final StravaAuthRepository stravaAuthRepository;
    private final WebClient.Builder webClientBuilder;

    /**
     * Gera URL de autorização do Strava
     */
    public String getAuthorizationUrl(UUID atletaId) {
        return UriComponentsBuilder
                .fromUriString(stravaProperties.getAuthorizationUri())
                .queryParam("client_id", stravaProperties.getClientId())
                .queryParam("redirect_uri", stravaProperties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", stravaProperties.getDefaultScopes())
                .queryParam("state", atletaId.toString()) // Para identificar o atleta no callback
                .build()
                .toUriString();
    }

    /**
     * Troca o código de autorização por tokens de acesso
     */
    @Transactional
    public StravaAuth exchangeCodeForToken(String code, Atleta atleta) {
        log.info("Trocando código de autorização por token para atleta: {}", atleta.getId());

        StravaTokenResponse response = webClientBuilder.build()
                .post()
                .uri(stravaProperties.getTokenUri())
                .header("Content-Type", "application/json")
                .bodyValue(buildTokenExchangeRequest(code))
                .retrieve()
                .bodyToMono(StravaTokenResponse.class)
                .block();

        if (response == null) {
            throw new RuntimeException("Falha ao obter token do Strava");
        }

        return saveOrUpdateStravaAuth(atleta, response);
    }

    /**
     * Atualiza token expirado usando refresh token
     */
    @Transactional
    public StravaAuth refreshAccessToken(StravaAuth stravaAuth) {
        log.info("Atualizando access token para atleta: {}", stravaAuth.getAtleta().getId());

        StravaTokenResponse response = webClientBuilder.build()
                .post()
                .uri(stravaProperties.getTokenUri())
                .header("Content-Type", "application/json")
                .bodyValue(buildTokenRefreshRequest(stravaAuth.getRefreshToken()))
                .retrieve()
                .bodyToMono(StravaTokenResponse.class)
                .block();

        if (response == null) {
            throw new RuntimeException("Falha ao atualizar token do Strava");
        }

        stravaAuth.setAccessToken(response.getAccessToken());
        stravaAuth.setRefreshToken(response.getRefreshToken());
        stravaAuth.setTokenExpiresAt(convertToLocalDateTime(response.getExpiresAt()));

        return stravaAuthRepository.save(stravaAuth);
    }

    /**
     * Obtém token válido (renova se necessário)
     */
    public StravaAuth getValidToken(UUID atletaId) {
        StravaAuth stravaAuth = stravaAuthRepository.findByAtletaId(atletaId)
                .orElseThrow(() -> new RuntimeException("Atleta não autorizou Strava"));

        if (stravaAuth.isTokenExpired()) {
            return refreshAccessToken(stravaAuth);
        }

        return stravaAuth;
    }

    private Object buildTokenExchangeRequest(String code) {
        return new TokenRequest(
                stravaProperties.getClientId(),
                stravaProperties.getClientSecret(),
                code,
                "authorization_code"
        );
    }

    private Object buildTokenRefreshRequest(String refreshToken) {
        return new TokenRequest(
                stravaProperties.getClientId(),
                stravaProperties.getClientSecret(),
                refreshToken,
                "refresh_token"
        );
    }

    private StravaAuth saveOrUpdateStravaAuth(Atleta atleta, StravaTokenResponse response) {
        StravaAuth stravaAuth = stravaAuthRepository.findByAtletaId(atleta.getId())
                .orElse(StravaAuth.builder()
                        .atleta(atleta)
                        .stravaAthleteId(response.getAthlete().getId())
                        .build());

        stravaAuth.setAccessToken(response.getAccessToken());
        stravaAuth.setRefreshToken(response.getRefreshToken());
        stravaAuth.setTokenExpiresAt(convertToLocalDateTime(response.getExpiresAt()));
        stravaAuth.setScope(stravaProperties.getDefaultScopes());

        return stravaAuthRepository.save(stravaAuth);
    }

    private LocalDateTime convertToLocalDateTime(Long unixTimestamp) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(unixTimestamp), ZoneOffset.UTC);
    }

    record TokenRequest(
            String client_id,
            String client_secret,
            String code,
            String grant_type
    ) {}
}
```

---

### **ETAPA 7: Repository**

#### StravaAuthRepository.java
```java
package com.menthoros.repository;

import com.menthoros.entity.StravaAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StravaAuthRepository extends JpaRepository<StravaAuth, UUID> {

    Optional<StravaAuth> findByAtletaId(UUID atletaId);

    Optional<StravaAuth> findByStravaAthleteId(Long stravaAthleteId);

    boolean existsByAtletaId(UUID atletaId);
}
```

---

### **ETAPA 8: Controller de Autenticação**

#### StravaAuthController.java
```java
package com.menthoros.controller;

import com.menthoros.entity.Atleta;
import com.menthoros.entity.StravaAuth;
import com.menthoros.repository.AtletaRepository;
import com.menthoros.services.StravaOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/strava")
@RequiredArgsConstructor
@Tag(name = "Strava Integration", description = "Endpoints para integração com Strava")
public class StravaAuthController {

    private final StravaOAuthService stravaOAuthService;
    private final AtletaRepository atletaRepository;

    @GetMapping("/auth")
    @Operation(summary = "Inicia fluxo de autenticação OAuth2 do Strava")
    public RedirectView initiateAuth(@RequestParam UUID atletaId) {
        log.info("Iniciando autenticação Strava para atleta: {}", atletaId);

        String authUrl = stravaOAuthService.getAuthorizationUrl(atletaId);
        return new RedirectView(authUrl);
    }

    @GetMapping("/callback")
    @Operation(summary = "Callback do OAuth2 do Strava")
    public RedirectView handleCallback(
            @RequestParam String code,
            @RequestParam String state, // atletaId
            @RequestParam(required = false) String error) {

        if (error != null) {
            log.error("Erro na autorização Strava: {}", error);
            return new RedirectView("http://localhost:3000/settings?strava=error");
        }

        try {
            UUID atletaId = UUID.fromString(state);
            Atleta atleta = atletaRepository.findById(atletaId)
                    .orElseThrow(() -> new RuntimeException("Atleta não encontrado"));

            StravaAuth stravaAuth = stravaOAuthService.exchangeCodeForToken(code, atleta);

            log.info("Strava conectado com sucesso para atleta: {}", atletaId);

            // Redireciona para o frontend com sucesso
            return new RedirectView("http://localhost:3000/settings?strava=success");

        } catch (Exception e) {
            log.error("Erro ao processar callback do Strava", e);
            return new RedirectView("http://localhost:3000/settings?strava=error");
        }
    }

    @GetMapping("/status/{atletaId}")
    @Operation(summary = "Verifica se atleta tem Strava conectado")
    public ResponseEntity<Map<String, Object>> getConnectionStatus(@PathVariable UUID atletaId) {
        boolean isConnected = stravaOAuthService.isConnected(atletaId);

        return ResponseEntity.ok(Map.of(
                "connected", isConnected,
                "atletaId", atletaId
        ));
    }

    @DeleteMapping("/disconnect/{atletaId}")
    @Operation(summary = "Desconecta conta Strava")
    public ResponseEntity<Void> disconnect(@PathVariable UUID atletaId) {
        stravaOAuthService.disconnect(atletaId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
```

---

## 📊 Estrutura de Dados

A integração Strava utiliza as seguintes tabelas:

- **tb_strava_auth**: Armazena credenciais de autenticação OAuth2 por atleta
- **tb_treino_realizado**: Estendida com campos `external_id` (ID do Strava) e `fonte_dados` (origem dos dados)
- **tb_atleta**: Referência para os atletas que autorizam a integração

---

## 🔐 Fluxo de Autenticação OAuth2

1. **Usuário clica em "Conectar Strava"**
   - Frontend redireciona para `/api/strava/auth?atletaId=UUID`

2. **Backend gera URL de autorização**
   - Código gerado pelo `StravaOAuthService.getAuthorizationUrl()`
   - Redireciona para `https://www.strava.com/oauth/authorize`

3. **Usuário autoriza no Strava**
   - Strava retorna `authorization_code` e `state` para `/api/strava/callback`

4. **Backend troca código por tokens**
   - `exchangeCodeForToken()` realiza POST em `https://www.strava.com/oauth/token`
   - Recebe `access_token`, `refresh_token` e `expires_at`
   - Salva em `tb_strava_auth`

5. **Token renovado automaticamente**
   - Método `isTokenExpired()` verifica se token expira em 5 minutos
   - `refreshAccessToken()` usa `refresh_token` para obter novo token

---

## 🔄 Sincronização de Atividades

### Próximos Passos para Implementação:

#### Fase 1: Service de Sincronização
- Implementar `StravaActivityService` para buscar atividades via API
- Métodos: `fetchActivities()`, `fetchActivityById()`, `syncActivities()`
- Mapeamento de `StravaActivityDto` para `TreinoRealizado`

#### Fase 2: Processamento de Dados
- Converter distância de metros para km
- Converter velocidade de m/s para pace (min/km)
- Mapear tipos de atividade Strava para tipos de treino Menthoros

#### Fase 3: Deduplicação
- Verificar por `external_id` antes de salvar
- Impedir importação duplicada de atividades

#### Fase 4: API Endpoints
- `POST /api/strava/sync/{atletaId}` - Sincronização manual
- `GET /api/strava/activities/{atletaId}` - Listar atividades sincronizadas
- Implementar paginação para grandes volumes

---

## 🪝 Webhooks do Strava

### Configuração de Webhooks

Webhooks permitem sincronização automática em tempo real quando atividades são criadas, atualizadas ou deletadas no Strava.

#### Próximos Passos:

1. **Criar `StravaWebhookController`**
   - `GET /api/strava/webhook` - Validação de subscription
   - `POST /api/strava/webhook` - Receber eventos

2. **Implementar validação de subscription**
   ```java
   hub.mode=subscribe
   hub.verify_token=WEBHOOK_TOKEN
   hub.challenge=random_string
   ```

3. **Registrar webhook via API Strava**
   ```bash
   curl -X POST https://www.strava.com/api/v3/push_subscriptions \
     -F client_id=YOUR_CLIENT_ID \
     -F client_secret=YOUR_CLIENT_SECRET \
     -F callback_url=https://your-domain.com/api/strava/webhook \
     -F verify_token=YOUR_WEBHOOK_TOKEN
   ```

4. **Processar eventos**
   - `create`: Nova atividade
   - `update`: Atividade atualizada
   - `delete`: Atividade removida

5. **Implementar fila assíncrona**
   - Processar eventos em background
   - Implementar retry logic para falhas

---

## 🗺️ Mapeamento de Dados

### Mapeamento Strava → Menthoros

| Campo Strava | Campo Menthoros | Conversão |
|--------------|-----------------|-----------|
| `id` | `external_id` | String |
| `name` | `descricao` | String |
| `type` | `tipo_treino` | Enum (Run, Ride, etc.) |
| `start_date_local` | `data_hora_inicio` | ZonedDateTime |
| `distance` (metros) | `distancia_km` | / 1000 |
| `moving_time` (seg) | `duracao_minutos` | / 60 |
| `total_elevation_gain` | `elevacao_metros` | Double |
| `average_speed` (m/s) | `pace_minuto_km` | Conversão especial |
| `average_heartrate` | `fc_media` | Double |
| `suffer_score` | `tss_calculado` | Integer |
| `perceived_exertion` | `percepcao_esforco` | Integer (RPE) |

---

## ✅ Testes e Validação

### Teste Manual do Fluxo Completo:

1. **Teste de Autenticação**
   - [ ] Acessar `/api/strava/auth?atletaId=seu-uuid`
   - [ ] Verificar redirecionamento para Strava
   - [ ] Autorizar acesso no Strava
   - [ ] Verificar retorno para callback com sucesso
   - [ ] Confirmar salvamento de tokens em `tb_strava_auth`

2. **Teste de Token Refresh**
   - [ ] Aguardar expiração de token (ou manipular manualmente)
   - [ ] Chamar endpoint que requer token
   - [ ] Verificar se token foi renovado automaticamente

3. **Teste de Sincronização** (após implementar)
   - [ ] Chamar `POST /api/strava/sync/{atletaId}`
   - [ ] Verificar importação de atividades
   - [ ] Validar mapeamento de dados
   - [ ] Confirmar deduplicação

4. **Teste de Webhooks** (após implementar)
   - [ ] Registrar webhook em produção
   - [ ] Criar atividade no Strava
   - [ ] Verificar recebimento do evento
   - [ ] Validar processamento

---

## 🔒 Segurança e Boas Práticas

### Checklist de Segurança

- [ ] **Client Secret** em variável de ambiente (NUNCA no código)
- [ ] **Tokens criptografados** no banco de dados
- [ ] **HTTPS obrigatório** em produção
- [ ] **Rate limiting** nos endpoints
- [ ] **Validação de webhook signature**
- [ ] **Logs sem informações sensíveis**
- [ ] **Token refresh automático** (verifica 5 minutos antes da expiração)
- [ ] **Desconexão segura** (revogação de tokens ao desconectar)

### Boas Práticas

1. **Rotação de tokens**: Implementar refresh automático
2. **Logs estruturados**: Nunca logar tokens ou senhas
3. **Validação de entrada**: Validar UUID, emails, etc.
4. **Tratamento de erros**: Não expor detalhes técnicos
5. **Criptografia**: Dados sensíveis sempre criptografados
6. **Rate limiting**: Proteger contra abuso de API

---

## 🗺️ Roadmap de Implementação

### 📅 Cronograma Sugerido

Este documento apresenta um plano de desenvolvimento em 6 semanas, dividido em 7 sprints.

### **SPRINT 1 - Fundação (Semana 1)**

**Objetivo**: Configurar infraestrutura básica e autenticação OAuth2

#### Tarefas:
- [ ] **1.1** Adicionar dependências ao `pom.xml`
  - `spring-boot-starter-oauth2-client`
  - `spring-boot-starter-webflux`

- [ ] **1.2** Configurar variáveis de ambiente
  - Criar `.env` com credenciais do Strava
  - Adicionar propriedades ao `application.yml`

- [ ] **1.3** Criar entidade `StravaAuth`
  - Arquivo: `src/main/java/com/menthoros/entity/StravaAuth.java`

- [ ] **1.4** Criar migration do banco
  - Arquivo: `src/main/resources/db/migration/V7__Create_strava_auth_table.sql`
  - Executar: `mvn flyway:migrate`

- [ ] **1.5** Criar DTOs de comunicação
  - `StravaTokenResponse.java`
  - `StravaAthleteDto.java`

- [ ] **1.6** Criar `StravaProperties` configuration

- [ ] **1.7** Criar `StravaAuthRepository`

**Entregáveis**: Estrutura de dados e configuração completa

---

### **SPRINT 2 - Autenticação OAuth2 (Semana 1-2)**

**Objetivo**: Implementar fluxo completo de autenticação

#### Tarefas:
- [ ] **2.1** Implementar `StravaOAuthService`
  - Método: `getAuthorizationUrl()`
  - Método: `exchangeCodeForToken()`
  - Método: `refreshAccessToken()`
  - Método: `getValidToken()`

- [ ] **2.2** Criar `StravaAuthController`
  - Endpoint: `GET /api/strava/auth`
  - Endpoint: `GET /api/strava/callback`
  - Endpoint: `GET /api/strava/status/{atletaId}`
  - Endpoint: `DELETE /api/strava/disconnect/{atletaId}`

- [ ] **2.3** Implementar tratamento de erros

- [ ] **2.4** Adicionar logs detalhados

- [ ] **2.5** Testes unitários
  - `StravaOAuthServiceTest.java`

- [ ] **2.6** Teste integração manual
  - Autorizar atleta via browser
  - Verificar tokens salvos no banco

**Entregáveis**: Autenticação OAuth2 funcionando end-to-end

---

### **SPRINT 3 - Sincronização de Atividades (Semana 2-3)**

**Objetivo**: Importar atividades do Strava

#### Tarefas:
- [ ] **3.1** Criar DTOs de Atividade
  - `StravaActivityDto.java`
  - `StravaSplitDto.java`

- [ ] **3.2** Implementar `StravaActivityService`
  - Método: `fetchActivities(atletaId, after, before)`
  - Método: `fetchActivityById(activityId)`
  - Método: `syncActivities(atletaId)`

- [ ] **3.3** Implementar mapeamento Strava → TreinoRealizado
  - Método: `mapStravaActivityToTreinoRealizado()`
  - Converter distância metros → km
  - Converter velocidade m/s → pace min/km
  - Mapear tipo de atividade

- [ ] **3.4** Adicionar lógica de deduplicação
  - Verificar por `external_id` antes de salvar

- [ ] **3.5** Criar `StravaActivityController`
  - Endpoint: `POST /api/strava/sync/{atletaId}`
  - Endpoint: `GET /api/strava/activities/{atletaId}`

- [ ] **3.6** Implementar paginação

- [ ] **3.7** Testes de integração

**Entregáveis**: Importação manual de atividades funcionando

---

### **SPRINT 4 - Webhooks (Semana 3-4)**

**Objetivo**: Sincronização em tempo real

#### Tarefas:
- [ ] **4.1** Criar `StravaWebhookController`
  - Endpoint: `GET /api/strava/webhook` (validação subscription)
  - Endpoint: `POST /api/strava/webhook` (receber eventos)

- [ ] **4.2** Implementar validação de subscription
  ```java
  hub.mode=subscribe
  hub.verify_token=WEBHOOK_TOKEN
  hub.challenge=random_string
  ```

- [ ] **4.3** Criar `StravaWebhookService`
  - Processar eventos: `create`, `update`, `delete`
  - Fila de processamento assíncrono

- [ ] **4.4** Registrar webhook via Strava API
  ```bash
  curl -X POST https://www.strava.com/api/v3/push_subscriptions \
    -F client_id=YOUR_CLIENT_ID \
    -F client_secret=YOUR_CLIENT_SECRET \
    -F callback_url=https://your-domain.com/api/strava/webhook \
    -F verify_token=YOUR_WEBHOOK_TOKEN
  ```

- [ ] **4.5** Implementar rate limiting

- [ ] **4.6** Adicionar retry logic para falhas

- [ ] **4.7** Testes com webhook simulator

**Entregáveis**: Sincronização automática em tempo real

---

### **SPRINT 5 - Cálculo de TSS (Semana 4)**

**Objetivo**: Calcular TSS baseado em dados do Strava

#### Tarefas:
- [ ] **5.1** Integrar Suffer Score do Strava
  - Mapear para campo `tssCalculado`

- [ ] **5.2** Implementar cálculo alternativo de TSS
  - Baseado em FC média vs FC limiar
  - Baseado em Pace médio vs Pace limiar

- [ ] **5.3** Criar service `TSSCalculatorService`
  - Método: `calculateFromHeartRate()`
  - Método: `calculateFromPace()`
  - Método: `calculateFromSufferScore()`

- [ ] **5.4** Adicionar lógica de escolha de método
  - Prioridade: Suffer Score > FC > Pace > RPE

- [ ] **5.5** Atualizar metadados do atleta
  - CTL, ATL, TSB após cada treino importado

**Entregáveis**: TSS calculado automaticamente

---

### **SPRINT 6 - Comparação Planejado vs Realizado (Semana 5)**

**Objetivo**: Comparar treinos planejados com realizados

#### Tarefas:
- [ ] **6.1** Implementar matching automático
  - Por data + tipo de treino
  - Por distância similar

- [ ] **6.2** Criar relatório de comparação
  - Distância planejada vs realizada
  - Pace planejado vs realizado
  - TSS planejado vs realizado

- [ ] **6.3** Endpoint de análise
  - `GET /api/treinos/comparacao/{treinoPlanejadoId}`

- [ ] **6.4** Dashboard de aderência
  - % treinos completados
  - Diferença média de volume
  - Diferença média de intensidade

**Entregáveis**: Análise de aderência ao plano

---

### **SPRINT 7 - Polimento e Testes (Semana 5-6)**

**Objetivo**: Garantir qualidade e segurança

#### Tarefas:
- [ ] **7.1** Segurança
  - Criptografar tokens no banco
  - Validar webhook signatures
  - Rate limiting em todos endpoints

- [ ] **7.2** Documentação
  - Swagger/OpenAPI completo
  - README com setup
  - Guia de troubleshooting

- [ ] **7.3** Testes
  - Cobertura > 80%
  - Testes de integração end-to-end
  - Testes de carga

- [ ] **7.4** Monitoramento
  - Logs estruturados
  - Métricas de sincronização
  - Alertas de falha

- [ ] **7.5** Deploy
  - Environment variables em produção
  - HTTPS configurado
  - Webhook subscription em produção

**Entregáveis**: Integração production-ready

---

### 🎯 Marcos de Entrega

| Marco | Descrição | Prazo Sugerido |
|-------|-----------|----------------|
| **M1** | Autenticação OAuth2 funcionando | Fim da Semana 1 |
| **M2** | Importação manual de atividades | Fim da Semana 2 |
| **M3** | Webhooks recebendo eventos | Fim da Semana 3 |
| **M4** | TSS calculado automaticamente | Fim da Semana 4 |
| **M5** | Comparação planejado vs realizado | Fim da Semana 5 |
| **M6** | Deploy em produção | Fim da Semana 6 |

---

### 🚀 Quick Start - Começar Hoje

#### Comandos para Iniciar (Sprint 1):

```bash
# 1. Adicionar dependências ao pom.xml
# (Copiar do guia principal)

# 2. Criar arquivo .env
cat > .env << EOF
STRAVA_CLIENT_ID=YOUR_CLIENT_ID
STRAVA_CLIENT_SECRET=YOUR_CLIENT_SECRET
STRAVA_REDIRECT_URI=http://localhost:8098/api/strava/callback
STRAVA_WEBHOOK_TOKEN=menthoros_webhook_secret
EOF

# 3. Criar estrutura de pastas
mkdir -p src/main/java/com/menthoros/entity
mkdir -p src/main/java/com/menthoros/dto/strava
mkdir -p src/main/java/com/menthoros/services
mkdir -p src/main/java/com/menthoros/controller
mkdir -p src/main/java/com/menthoros/repository
mkdir -p src/main/resources/db/migration

# 4. Compilar
mvn clean compile

# 5. Executar migration
mvn flyway:migrate
```

---

## ✅ Checklist Final

Antes de considerar a integração completa:

### Funcionalidade
- [ ] Atleta consegue autorizar via OAuth2
- [ ] Tokens são renovados automaticamente
- [ ] Atividades são importadas corretamente
- [ ] Webhooks recebem eventos em tempo real
- [ ] TSS é calculado com precisão
- [ ] Comparação planejado vs realizado funciona

### Qualidade
- [ ] Cobertura de testes > 80%
- [ ] Documentação completa
- [ ] Logs estruturados
- [ ] Error handling robusto

### Segurança
- [ ] Credenciais em variáveis de ambiente
- [ ] Tokens criptografados
- [ ] HTTPS em produção
- [ ] Rate limiting ativo
- [ ] Webhook signature validada

### Performance
- [ ] Sincronização < 5 segundos
- [ ] Paginação implementada
- [ ] Cache de tokens
- [ ] Processamento assíncrono

---

## 📚 Referências

- [Strava API Documentation](https://developers.strava.com/docs/reference/)
- [OAuth 2.0 Authorization Flow](https://developers.strava.com/docs/authentication/)
- [Webhook Events](https://developers.strava.com/docs/webhooks/)
- [Activity Types](https://developers.strava.com/docs/reference/#api-models-ActivityType)

---

**Consolidado por**: Claude Code
**Data de Consolidação**: 2026-03-19
**Versão**: 2.0.0
