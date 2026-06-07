# Integracao Automatica - Garmin e Strava

**Data:** 2026-02-10
**Objetivo:** Importar automaticamente treinos realizados com etapas detalhadas (laps) a partir de plataformas externas.
**Pre-requisito:** Fase 1 de EtapaRealizada implementada (entidade, DTOs, mapper, migration)
**Status:** Planejamento

---

## 1. Visao Geral

### Problema

Atletas registram treinos no Garmin/Strava com dados ricos (laps, FC por volta, pace por split) que hoje sao perdidos. O lancamento manual no Menthoros captura apenas metricas agregadas. Com `EtapaRealizada` implementada, falta o pipeline automatico para popular essas etapas.

### Fluxo Proposto

```
Atleta completa treino
        |
        v
Garmin/Strava detecta atividade
        |
        v
Webhook notifica Menthoros (POST /api/webhooks/strava ou /api/webhooks/garmin)
        |
        v
Menthoros busca detalhes da atividade + laps via API
        |
        v
Servico de importacao mapeia laps → EtapaRealizada
        |
        v
Inferencia de tipo de etapa (aquecimento, intervalo, recuperacao, desaquecimento)
        |
        v
Matching opcional com TreinoPlanejado do dia
        |
        v
TreinoServiceImpl.addTreino() persiste tudo (cascade)
```

---

## 2. Comparacao das APIs

| Aspecto | Strava | Garmin |
|---------|--------|--------|
| Autenticacao | OAuth 2.0 (refresh tokens) | OAuth 1.0a |
| Webhook | Sim (push com validacao GET) | Sim (push ou ping/pull) |
| Dados de laps | `GET /activities/{id}/laps` (JSON) | Activity Details Summary (JSON) ou FIT file |
| Campos por lap | elapsed_time, distance, avg_speed, max_speed, avg_heartrate, max_heartrate, lap_index | startTimeInSeconds, duration, distance, avgSpeed, avgHR, maxHR (+ samples) |
| Rate limit | 200 req/15min, 2000/dia | Varia por contrato |
| Precisao de laps | Boa (marcacao manual ou auto-lap) | Auto-lap por km/milha (smart recording pode perder precisao nos limites) |
| Formato raw | Nao disponivel via API publica | FIT file disponivel (binario, mais preciso) |
| Dificuldade | Baixa (API bem documentada) | Media (OAuth1, FIT parsing opcional) |

**Recomendacao:** Comecar pelo Strava (API mais simples, OAuth2, JSON nativo). Garmin como segundo passo.

---

## 3. Arquitetura Proposta

### 3.1 Estrutura de Pacotes

```
com.menthoros/
├── integration/
│   ├── common/
│   │   ├── ExternalActivityImporter.java      ← interface comum
│   │   ├── ImportResult.java                   ← resultado da importacao
│   │   └── EtapaTypeInferenceService.java     ← inferencia de tipo de etapa
│   │
│   ├── strava/
│   │   ├── StravaOAuthService.java            ← fluxo OAuth2 + refresh token
│   │   ├── StravaApiClient.java               ← chamadas REST para Strava
│   │   ├── StravaWebhookController.java       ← recebe webhooks
│   │   ├── StravaActivityImporter.java        ← impl de ExternalActivityImporter
│   │   └── dto/
│   │       ├── StravaActivityDto.java
│   │       ├── StravaLapDto.java
│   │       └── StravaWebhookEventDto.java
│   │
│   └── garmin/
│       ├── GarminOAuthService.java            ← fluxo OAuth1.0a
│       ├── GarminApiClient.java               ← chamadas REST para Garmin
│       ├── GarminPushController.java          ← recebe push notifications
│       ├── GarminActivityImporter.java        ← impl de ExternalActivityImporter
│       └── dto/
│           ├── GarminActivityDetailDto.java
│           ├── GarminLapDto.java
│           └── GarminPushNotificationDto.java
│
├── entity/
│   └── IntegracaoExterna.java                 ← tokens OAuth por atleta
│
└── repository/
    └── IntegracaoExternaRepository.java
```

### 3.2 Interface Comum

```java
public interface ExternalActivityImporter {

    FonteDados getFonteDados(); // STRAVA, GARMIN

    ImportResult importar(String externalActivityId, UUID atletaId);
}
```

```java
public record ImportResult(
    TreinoRealizado treino,
    int etapasImportadas,
    boolean matchedComPlanejado,
    String mensagem
) {}
```

Cada plataforma implementa `ExternalActivityImporter`. O servico de importacao resolve qual usar baseado na `FonteDados`.

### 3.3 Entidade para Tokens OAuth

```java
@Entity
@Table(name = "tb_integracao_externa")
public class IntegracaoExterna {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    @Enumerated(EnumType.STRING)
    @Column(name = "plataforma", nullable = false)
    private FonteDados plataforma; // STRAVA, GARMIN

    @Column(name = "external_athlete_id")
    private String externalAthleteId; // ID do atleta na plataforma

    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "token_expira_em")
    private LocalDateTime tokenExpiraEm;

    @Column(name = "scopes")
    private String scopes; // "activity:read,activity:read_all"

    @Column(name = "ativo")
    private Boolean ativo = true;

    @Column(name = "ultima_sincronizacao")
    private LocalDateTime ultimaSincronizacao;
}
```

---

## 4. Fluxo Strava (Detalhado)

### 4.1 Autenticacao OAuth2

```
1. Atleta clica "Conectar Strava" no Menthoros
2. Redirect para: https://www.strava.com/oauth/authorize
   ?client_id={CLIENT_ID}
   &redirect_uri={CALLBACK_URL}
   &response_type=code
   &scope=activity:read_all
3. Atleta autoriza
4. Strava redireciona para callback com ?code=XXX
5. Menthoros troca code por access_token + refresh_token
   POST https://www.strava.com/oauth/token
6. Salva tokens em IntegracaoExterna
```

Refresh automatico quando `token_expira_em` esta proximo:
```java
public String getValidAccessToken(IntegracaoExterna integracao) {
    if (integracao.getTokenExpiraEm().isBefore(LocalDateTime.now().plusMinutes(5))) {
        // Refresh token
        var response = stravaClient.refreshToken(integracao.getRefreshToken());
        integracao.setAccessToken(response.accessToken());
        integracao.setRefreshToken(response.refreshToken());
        integracao.setTokenExpiraEm(response.expiresAt());
        integracaoRepository.save(integracao);
    }
    return integracao.getAccessToken();
}
```

### 4.2 Webhook

**Registro (uma vez):**
```
POST https://www.strava.com/api/v3/push_subscriptions
{
  "client_id": "{CLIENT_ID}",
  "client_secret": "{CLIENT_SECRET}",
  "callback_url": "https://api.menthoros.com/api/webhooks/strava",
  "verify_token": "{TOKEN_VERIFICACAO}"
}
```

**Validacao (Strava envia GET para confirmar):**
```java
@GetMapping("/api/webhooks/strava")
public Map<String, String> validarWebhook(
        @RequestParam("hub.mode") String mode,
        @RequestParam("hub.verify_token") String verifyToken,
        @RequestParam("hub.challenge") String challenge) {

    if ("subscribe".equals(mode) && VERIFY_TOKEN.equals(verifyToken)) {
        return Map.of("hub.challenge", challenge);
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN);
}
```

**Recebimento de eventos (Strava envia POST):**
```java
@PostMapping("/api/webhooks/strava")
public ResponseEntity<Void> receberEvento(@RequestBody StravaWebhookEventDto event) {
    // Responder 200 imediatamente (Strava exige resposta rapida)
    // Processar de forma assincrona
    if ("activity".equals(event.objectType()) && "create".equals(event.aspectType())) {
        applicationEventPublisher.publishEvent(
            new AtividadeExternaDetectadaEvent(
                FonteDados.STRAVA,
                String.valueOf(event.objectId()),
                String.valueOf(event.ownerId())
            )
        );
    }
    return ResponseEntity.ok().build();
}
```

### 4.3 Busca de Atividade + Laps

```java
// StravaApiClient.java

public StravaActivityDto getActivity(String activityId, String accessToken) {
    // GET https://www.strava.com/api/v3/activities/{id}
    // Headers: Authorization: Bearer {accessToken}
    return restClient.get()
        .uri("/activities/{id}", activityId)
        .header("Authorization", "Bearer " + accessToken)
        .retrieve()
        .body(StravaActivityDto.class);
}

public List<StravaLapDto> getLaps(String activityId, String accessToken) {
    // GET https://www.strava.com/api/v3/activities/{id}/laps
    return restClient.get()
        .uri("/activities/{id}/laps", activityId)
        .header("Authorization", "Bearer " + accessToken)
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
}
```

### 4.4 DTOs Strava

```java
// Resposta de GET /activities/{id}/laps
public record StravaLapDto(
    Long id,
    int lapIndex,
    float distance,          // metros
    float elapsedTime,       // segundos
    float movingTime,        // segundos
    float averageSpeed,      // m/s
    float maxSpeed,          // m/s
    float averageHeartrate,
    float maxHeartrate,
    float averageCadence,
    float averageWatts
) {}

public record StravaActivityDto(
    Long id,
    String name,
    String sportType,        // Run, TrailRun, etc
    float distance,          // metros
    int movingTime,          // segundos
    int elapsedTime,
    float averageSpeed,
    float maxSpeed,
    float averageHeartrate,
    float maxHeartrate,
    String startDateLocal,   // ISO 8601
    float totalElevationGain
) {}
```

---

## 5. Mapeamento Laps → EtapaRealizada

### 5.1 Conversao Direta

```java
// StravaActivityImporter.java

private List<EtapaRealizada> converterLaps(List<StravaLapDto> laps) {
    return IntStream.range(0, laps.size())
        .mapToObj(i -> {
            var lap = laps.get(i);
            return EtapaRealizada.builder()
                .ordem(i + 1)
                .tipoEtapa(inferirTipoEtapa(lap, i, laps.size()))
                .distanciaKm(BigDecimal.valueOf(lap.distance() / 1000.0)
                    .setScale(3, RoundingMode.HALF_UP))
                .duracao(Duration.ofSeconds((long) lap.elapsedTime()))
                .fcMedia(Math.round(lap.averageHeartrate()))
                .fcMax(Math.round(lap.maxHeartrate()))
                .paceMedia(calcularPace(lap.distance(), lap.movingTime()))
                .velocidadeMedia((double) Math.round(lap.averageSpeed() * 3.6 * 10) / 10) // m/s → km/h
                .cadenciaMedia(Math.round(lap.averageCadence() * 2)) // Strava retorna half-cadence
                .potenciaMedia(Math.round(lap.averageWatts()))
                .build();
        })
        .toList();
}

private Duration calcularPace(float distanciaMetros, float tempoSegundos) {
    if (distanciaMetros <= 0) return null;
    double segundosPorKm = (tempoSegundos / distanciaMetros) * 1000;
    return Duration.ofSeconds(Math.round(segundosPorKm));
}
```

### 5.2 Inferencia de Tipo de Etapa

A inferencia e o ponto mais complexo. Abordagem por heuristicas:

```java
@Service
public class EtapaTypeInferenceService {

    /**
     * Infere o tipo de cada etapa baseado em posicao, FC e pace.
     *
     * Estrategia:
     * 1. Se treino planejado vinculado existe, usar etapas planejadas como referencia
     * 2. Senao, usar heuristicas baseadas em FC e pace relativos ao treino
     */
    public void inferirTipos(List<EtapaRealizada> etapas, TreinoPlanejado planejado, Atleta atleta) {
        if (planejado != null && planejado.getEtapas() != null && !planejado.getEtapas().isEmpty()) {
            inferirPorComparacaoComPlanejado(etapas, planejado.getEtapas());
        } else {
            inferirPorHeuristica(etapas, atleta);
        }
    }

    /**
     * Heuristica baseada em posicao e intensidade relativa:
     *
     * - Primeiro lap com FC < media geral → AQUECIMENTO
     * - Ultimo lap com FC < media geral → DESAQUECIMENTO
     * - Laps com FC > 85% FCmax ou pace < pace medio → INTERVALADO
     * - Laps com FC < 70% FCmax entre intervalados → RECUPERACAO
     * - Demais → PRINCIPAL
     */
    private void inferirPorHeuristica(List<EtapaRealizada> etapas, Atleta atleta) {
        if (etapas.isEmpty()) return;

        double fcMedia = etapas.stream()
            .filter(e -> e.getFcMedia() != null)
            .mapToInt(EtapaRealizada::getFcMedia)
            .average()
            .orElse(0);

        Integer fcMax = atleta.getFcMaxima();

        for (int i = 0; i < etapas.size(); i++) {
            EtapaRealizada etapa = etapas.get(i);
            if (etapa.getFcMedia() == null) {
                etapa.setTipoEtapa("PRINCIPAL");
                continue;
            }

            boolean primeiro = (i == 0);
            boolean ultimo = (i == etapas.size() - 1);
            boolean fcBaixa = etapa.getFcMedia() < fcMedia * 0.9;
            boolean fcAlta = fcMax != null && etapa.getFcMedia() > fcMax * 0.85;

            if (primeiro && fcBaixa) {
                etapa.setTipoEtapa("AQUECIMENTO");
            } else if (ultimo && fcBaixa) {
                etapa.setTipoEtapa("DESAQUECIMENTO");
            } else if (fcAlta) {
                etapa.setTipoEtapa("INTERVALADO");
            } else if (fcBaixa && i > 0 && "INTERVALADO".equals(etapas.get(i - 1).getTipoEtapa())) {
                etapa.setTipoEtapa("RECUPERACAO");
            } else {
                etapa.setTipoEtapa("PRINCIPAL");
            }
        }
    }

    /**
     * Quando existe treino planejado, tenta alinhar laps com etapas planejadas
     * por ordem e tipo esperado.
     */
    private void inferirPorComparacaoComPlanejado(
            List<EtapaRealizada> etapas,
            List<EtapaTreino> planejadas) {

        // Estrategia simples: se quantidade de laps ~ quantidade de etapas planejadas,
        // mapear 1:1 por ordem e copiar tipo
        if (etapas.size() == planejadas.size()) {
            for (int i = 0; i < etapas.size(); i++) {
                etapas.get(i).setTipoEtapa(planejadas.get(i).getTipoEtapa());
                etapas.get(i).setEtapaPlanejada(planejadas.get(i));
            }
            return;
        }

        // Se quantidades diferem, usar heuristica mas tentar match parcial
        // por tipo de etapa dominante (aquecimento no inicio, desaquecimento no fim)
        if (!etapas.isEmpty() && !planejadas.isEmpty()) {
            etapas.get(0).setTipoEtapa(planejadas.get(0).getTipoEtapa());
            etapas.get(etapas.size() - 1).setTipoEtapa(
                planejadas.get(planejadas.size() - 1).getTipoEtapa());
        }

        // Laps intermediarios: inferir por FC/pace
        // (melhoria futura: algoritmo de alinhamento por distancia acumulada)
    }
}
```

### 5.3 Matching com Treino Planejado

```java
/**
 * Tenta encontrar um TreinoPlanejado para a data e tipo do treino importado.
 */
private Optional<TreinoPlanejado> matchComPlanejado(UUID atletaId, LocalDate data, TipoTreino tipo) {
    return treinoPlanejadoRepository
        .findByAtletaIdAndDataTreinoAndStatusTreino(atletaId, data, TreinoExecucaoStatus.PENDENTE)
        .stream()
        .filter(p -> p.getTipoTreino() == tipo)
        .findFirst();
}
```

---

## 6. Fluxo Garmin (Resumo)

Similar ao Strava, com diferencas:

| Etapa | Strava | Garmin |
|-------|--------|--------|
| Auth | OAuth 2.0 (Spring Security OAuth2 Client) | OAuth 1.0a (assinatura HMAC-SHA1) |
| Webhook | POST para callback, validacao via GET | Push notification para callback, responder 200 |
| Dados | JSON nativo (laps endpoint separado) | JSON summary + FIT file para precisao |
| Laps | Array de laps com metricas completas | Array de timestamps + samples (precisa interpolar) |
| Cadencia | Half-cadence (multiplicar por 2) | Cadencia real |

**Nota sobre FIT files:** Para treinos intervalados com laps manuais (atleta apertou botao), o JSON do Garmin e suficiente. Para auto-laps (a cada km), o FIT file permite extrair sessions e laps com mais precisao usando o FIT SDK.

---

## 7. Tratamento de Erros e Resiliencia

### 7.1 Retry com Backoff

```java
// Webhook pode falhar por indisponibilidade da API externa
@Retryable(
    retryFor = { RestClientException.class },
    maxAttempts = 3,
    backoff = @Backoff(delay = 5000, multiplier = 2)
)
public ImportResult importarAtividade(FonteDados fonte, String externalId, UUID atletaId) {
    // ...
}
```

### 7.2 Deduplicacao

Ja existe no `TreinoServiceImpl.buscarTreinoDuplicado()`:
```java
// Verifica por fonteDados + externalId antes de salvar
Optional<TreinoRealizado> duplicado = treinoRealizadoRepository
    .findByFonteDadosAndExternalId(fonteDados, externalId);
```

### 7.3 Fila de Processamento

Para evitar timeout no webhook (Strava exige resposta rapida):
```
Webhook recebe evento → responde 200 imediatamente
                      → publica Spring Event async
                      → listener processa importacao em background
                      → se falhar, salva em fila de retry
```

---

## 8. Configuracao

### 8.1 application.yml

```yaml
menthoros:
  integration:
    strava:
      enabled: true
      client-id: ${STRAVA_CLIENT_ID}
      client-secret: ${STRAVA_CLIENT_SECRET}
      webhook-verify-token: ${STRAVA_WEBHOOK_VERIFY_TOKEN}
      redirect-uri: ${STRAVA_REDIRECT_URI:https://api.menthoros.com/api/oauth/strava/callback}
      api-base-url: https://www.strava.com/api/v3
    garmin:
      enabled: false  # habilitar quando implementado
      consumer-key: ${GARMIN_CONSUMER_KEY}
      consumer-secret: ${GARMIN_CONSUMER_SECRET}
```

### 8.2 Dependencias Maven

```xml
<!-- OAuth2 Client (Strava) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>

<!-- Retry para resiliencia -->
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
</dependency>

<!-- FIT SDK (Garmin - quando necessario) -->
<!-- https://developer.garmin.com/fit/download/ -->
```

---

## 9. Seguranca

- **Tokens OAuth criptografados** em banco (usar `@Convert` com AES ou Jasypt)
- **Webhook verificado** - Strava envia verify_token, Garmin assina com HMAC
- **Scopes minimos** - apenas `activity:read_all` (Strava), sem escrita
- **Revogacao** - endpoint para atleta desconectar plataforma (revogar token + desativar integracao)
- **HTTPS obrigatorio** - webhooks so funcionam com HTTPS

---

## 10. Checklist de Implementacao

### Etapa 1 - Infraestrutura Base

- [ ] Criar entidade `IntegracaoExterna` + migration
- [ ] Criar `IntegracaoExternaRepository`
- [ ] Criar interface `ExternalActivityImporter`
- [ ] Criar `EtapaTypeInferenceService`
- [ ] Criar evento `AtividadeExternaDetectadaEvent` + listener async
- [ ] Configurar `application.yml` com properties de integracao
- [ ] Adicionar dependencias Maven (oauth2-client, spring-retry)

### Etapa 2 - Strava

- [ ] Implementar `StravaOAuthService` (authorize, callback, refresh)
- [ ] Implementar `StravaApiClient` (getActivity, getLaps)
- [ ] Implementar `StravaWebhookController` (validacao GET + recebimento POST)
- [ ] Implementar `StravaActivityImporter` (conversao laps → EtapaRealizada)
- [ ] Criar DTOs Strava (activity, lap, webhook event)
- [ ] Registrar webhook subscription no Strava
- [ ] Testar fluxo completo: atividade no Strava → webhook → importacao → etapas
- [ ] Testar deduplicacao (mesma atividade importada 2x)
- [ ] Testar refresh de token expirado

### Etapa 3 - Garmin

- [ ] Implementar `GarminOAuthService` (OAuth 1.0a)
- [ ] Implementar `GarminApiClient` (activity details, FIT file download)
- [ ] Implementar `GarminPushController` (recebimento de push notifications)
- [ ] Implementar `GarminActivityImporter`
- [ ] Criar DTOs Garmin
- [ ] (Opcional) Integrar FIT SDK para parsing de arquivos FIT
- [ ] Testar fluxo completo

### Etapa 4 - Polimento

- [ ] Endpoint para atleta conectar/desconectar plataforma
- [ ] Criptografia de tokens em banco
- [ ] Dashboard de status de sincronizacao por atleta
- [ ] Fila de retry para importacoes falhas
- [ ] Metricas de monitoramento (importacoes/dia, falhas, latencia)

---

## 11. Riscos e Mitigacoes

| Risco | Impacto | Mitigacao |
|-------|---------|-----------|
| Rate limit do Strava (200 req/15min) | Medio | Queue com throttling, processar em batch |
| Webhook instabilidade (Strava reportou problemas em 2025) | Medio | Fallback com polling periodico |
| Auto-laps nao correspondem a etapas do treino | Alto | Inferencia por FC/pace + edicao manual pos-importacao |
| Token expirado entre webhook e fetch | Baixo | Refresh automatico antes de cada chamada |
| Garmin OAuth 1.0a complexo | Baixo | Usar biblioteca `signpost` ou Spring Security OAuth1 |
| FIT file parsing complexo | Medio | Comecar com JSON summary, FIT como melhoria futura |
| Atleta revoga acesso na plataforma | Baixo | Webhook de deauthorization (Strava envia evento) |

---

## 12. Fontes

- [Strava API v3 Reference](https://developers.strava.com/docs/reference/)
- [Strava Webhook Events API](https://developers.strava.com/docs/webhooks/)
- [Strava Getting Started](https://developers.strava.com/docs/getting-started/)
- [Garmin Activity API](https://developer.garmin.com/gc-developer-program/activity-api/)
- [Garmin Health API](https://developer.garmin.com/gc-developer-program/health-api/)
- [Garmin FIT SDK - Activity File Types](https://developer.garmin.com/fit/file-types/activity/)
- [Integrando Garmin em app de corrida (Ben Studio)](https://benestudio.co/integrating-garmin-into-coopah-running-app/)
