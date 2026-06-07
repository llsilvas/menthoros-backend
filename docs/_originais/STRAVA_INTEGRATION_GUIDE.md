# 🏃 Guia Completo de Integração Strava - Menthoros

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

### 1. Informações Necessárias

Após criar seu app no Strava ([https://www.strava.com/settings/api](https://www.strava.com/settings/api)), você recebeu:

| Campo | Descrição | Exemplo |
|-------|-----------|---------|
| **Client ID** | ID público do seu aplicativo | `123456` |
| **Client Secret** | Chave secreta (NUNCA exponha!) | `abc123def456...` |
| **Authorization Callback Domain** | Domínio autorizado | `localhost` ou `app.menthoros.com` |

### 2. Configuração no application.yml

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

### 3. Variáveis de Ambiente (.env)

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

## 📊 Próximos Passos

### Fase 2: Sincronização de Atividades
- Service para importar atividades do Strava
- Mapeamento de StravaActivity → TreinoRealizado
- Cálculo de TSS baseado em dados do Strava
- Job agendado para sincronização automática

### Fase 3: Webhooks
- Endpoint para receber eventos do Strava em tempo real
- Validação de subscription
- Processamento de eventos: create, update, delete

### Fase 4: Métricas Avançadas
- Integração de Suffer Score como TSS
- Análise de splits e pace zones
- Comparação com treinos planejados

---

## 🔒 Segurança

### Checklist de Segurança
- [ ] Client Secret em variável de ambiente (NUNCA no código)
- [ ] Tokens criptografados no banco de dados
- [ ] HTTPS obrigatório em produção
- [ ] Rate limiting nos endpoints
- [ ] Validação de webhook signature
- [ ] Logs sem informações sensíveis

---

## 📚 Referências

- [Strava API Documentation](https://developers.strava.com/docs/reference/)
- [OAuth 2.0 Authorization Flow](https://developers.strava.com/docs/authentication/)
- [Webhook Events](https://developers.strava.com/docs/webhooks/)
- [Activity Types](https://developers.strava.com/docs/reference/#api-models-ActivityType)

---

**Autor**: Claude Code
**Data**: 2025-10-10
**Versão**: 1.0.0