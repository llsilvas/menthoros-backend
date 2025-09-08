# 🔧 BOAS PRÁTICAS PARA INTEGRAÇÕES DE TERCEIROS

## Menthoros - Strava & Garmin Integration Guide

---

## 🎯 **PRINCÍPIOS FUNDAMENTAIS**

### **1. Security First**
- **OAuth 2.0 Flow**: Nunca armazenar senhas, sempre usar tokens
- **Token Management**: Refresh automático, expiração segura
- **State Parameter**: Prevenir CSRF attacks
- **HTTPS Only**: Todas as comunicações criptografadas

### **2. Resilience by Design**
- **Circuit Breaker Pattern**: Falhas externas não quebram o sistema
- **Graceful Degradation**: Sistema funciona sem integrações
- **Retry Strategy**: Tentativas inteligentes com backoff
- **Timeouts**: Evitar hanging requests

### **3. Data Privacy**
- **Minimal Scope**: Solicitar apenas permissões necessárias
- **Local Encryption**: Tokens armazenados criptografados
- **Data Retention**: Políticas claras de retenção
- **User Consent**: Transparência total sobre uso dos dados

---

## 🔐 **IMPLEMENTAÇÃO OAUTH SEGURA**

### **Strava OAuth Flow - Spring Boot**
```java
@Configuration
@EnableWebSecurity
public class OAuthConfig {
    
    @Value("${strava.client-id}")
    private String stravaClientId;
    
    @Value("${strava.client-secret}")
    private String stravaClientSecret;
    
    @Bean
    @ConfigurationProperties("spring.security.oauth2.client.registration.strava")
    public ClientRegistration stravaClientRegistration() {
        return ClientRegistration.withRegistrationId("strava")
            .clientId(stravaClientId)
            .clientSecret(stravaClientSecret)
            .scope("activity:read_all", "profile:read_all")
            .authorizationUri("https://www.strava.com/oauth/authorize")
            .tokenUri("https://www.strava.com/oauth/token")
            .userInfoUri("https://www.strava.com/api/v3/athlete")
            .userNameAttributeName("id")
            .redirectUri("{baseUrl}/oauth2/callback/{registrationId}")
            .build();
    }
    
    @Bean
    public InMemoryClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(stravaClientRegistration());
    }
}
```

### **Token Security & Management**
```java
@Entity
@Table(name = "oauth_tokens")
public class OAuthToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "encrypted_access_token")
    private String encryptedAccessToken;
    
    @Column(name = "encrypted_refresh_token") 
    private String encryptedRefreshToken;
    
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime refreshedAt;
    
    // Métodos para encrypt/decrypt tokens
    public void setAccessToken(String token) {
        this.encryptedAccessToken = encryptionService.encrypt(token);
    }
    
    public String getAccessToken() {
        return encryptionService.decrypt(this.encryptedAccessToken);
    }
}

@Service
public class TokenEncryptionService {
    
    @Value("${app.encryption.key}")
    private String encryptionKey;
    
    public String encrypt(String plainText) {
        // Implementar AES encryption
        // Usar chave específica do ambiente
        return AESUtil.encrypt(plainText, encryptionKey);
    }
    
    public String decrypt(String encryptedText) {
        return AESUtil.decrypt(encryptedText, encryptionKey);
    }
}
```

---

## 🔄 **PADRÕES DE INTEGRAÇÃO RESILIENTES**

### **Circuit Breaker Implementation**
```java
@Component
public class StravaAPIClient {
    
    private final RestTemplate restTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    
    public StravaAPIClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(
            CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .minimumNumberOfCalls(5)
                .build()
        );
    }
    
    @CircuitBreaker(name = "strava-api", fallbackMethod = "fallbackGetActivities")
    @Retry(name = "strava-api")
    @TimeLimiter(name = "strava-api")
    public CompletableFuture<List<StravaActivity>> getActivities(String accessToken, LocalDateTime since) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        
        String url = String.format(
            "https://www.strava.com/api/v3/athlete/activities?after=%d&per_page=50",
            since.toEpochSecond(ZoneOffset.UTC)
        );
        
        ResponseEntity<List<StravaActivity>> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            new ParameterizedTypeReference<List<StravaActivity>>() {}
        );
        
        return CompletableFuture.completedFuture(response.getBody());
    }
    
    // Fallback method
    public CompletableFuture<List<StravaActivity>> fallbackGetActivities(
            String accessToken, LocalDateTime since, Exception ex) {
        log.warn("Strava API fallback activated due to: {}", ex.getMessage());
        
        // Retornar dados do cache ou lista vazia
        return CompletableFuture.completedFuture(
            getCachedActivities(accessToken).orElse(Collections.emptyList())
        );
    }
}
```

### **Retry Strategy com Backoff Exponencial**
```java
@Configuration
public class ResilienceConfig {
    
    @Bean
    public Retry stravaApiRetry() {
        return Retry.of("strava-api", RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofSeconds(2))
            .intervalFunction(IntervalFunction.ofExponentialBackoff(
                Duration.ofSeconds(2), 2.0
            ))
            .retryOnException(throwable -> 
                throwable instanceof ResourceAccessException ||
                throwable instanceof HttpServerErrorException ||
                (throwable instanceof HttpClientErrorException && 
                 ((HttpClientErrorException) throwable).getStatusCode().value() == 429) // Rate limit
            )
            .build());
    }
    
    @Bean
    public TimeLimiter stravaApiTimeLimiter() {
        return TimeLimiter.of("strava-api", TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(10))
            .build());
    }
}
```

---

## 📊 **RATE LIMITING & CACHING**

### **Intelligent Caching Strategy**
```java
@Service
@CacheConfig(cacheNames = "strava-activities")
public class StravaDataService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final RateLimiter rateLimiter;
    
    public StravaDataService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        // Strava allows 600 requests per 15 minutes, 40 requests per minute
        this.rateLimiter = RateLimiter.create(0.5); // 1 request per 2 seconds to be safe
    }
    
    @Cacheable(key = "'activities:' + #atletaId + ':' + #since.toString()", 
               condition = "#since.isAfter(T(java.time.LocalDateTime).now().minusHours(1))")
    public List<StravaActivity> getActivitiesWithCache(UUID atletaId, LocalDateTime since) {
        // Rate limiting
        rateLimiter.acquire();
        
        OAuthToken token = getValidToken(atletaId);
        return stravaAPIClient.getActivities(token.getAccessToken(), since)
            .orTimeout(15, TimeUnit.SECONDS)
            .exceptionally(throwable -> {
                log.error("Failed to fetch Strava activities", throwable);
                return getCachedActivitiesFromDB(atletaId, since);
            })
            .join();
    }
    
    // Inteligent cache warming
    @Scheduled(fixedRate = 3600000) // Every hour
    public void warmupCache() {
        List<UUID> activeUsers = getActiveUsersWithStravaIntegration();
        
        activeUsers.parallelStream()
            .limit(10) // Process max 10 users per batch to respect rate limits
            .forEach(atletaId -> {
                try {
                    getActivitiesWithCache(atletaId, LocalDateTime.now().minusDays(7));
                    Thread.sleep(2000); // Respect rate limits
                } catch (Exception e) {
                    log.warn("Cache warmup failed for user {}: {}", atletaId, e.getMessage());
                }
            });
    }
}
```

---

## 🔄 **WEBHOOK & REAL-TIME SYNC**

### **Strava Webhook Handler**
```java
@RestController
@RequestMapping("/api/webhooks")
@Validated
public class StravaWebhookController {
    
    @Value("${strava.webhook.verify-token}")
    private String webhookVerifyToken;
    
    private final StravaWebhookService webhookService;
    
    // Webhook subscription verification
    @GetMapping("/strava")
    public ResponseEntity<?> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.challenge") String challenge,
            @RequestParam("hub.verify_token") String verifyToken) {
        
        if ("subscribe".equals(mode) && webhookVerifyToken.equals(verifyToken)) {
            return ResponseEntity.ok(Map.of("hub.challenge", challenge));
        }
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    
    // Webhook event handling
    @PostMapping("/strava")
    public ResponseEntity<?> handleStravaWebhook(
            @RequestBody @Valid StravaWebhookEvent event,
            HttpServletRequest request) {
        
        // Verify webhook signature (if configured)
        if (!verifyWebhookSignature(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        // Process asynchronously to return 200 quickly
        webhookService.processEventAsync(event);
        
        return ResponseEntity.ok().build();
    }
    
    private boolean verifyWebhookSignature(HttpServletRequest request) {
        // Implement signature verification if Strava supports it
        // For now, just validate the source IP or use other verification
        return true;
    }
}

@Service
public class StravaWebhookService {
    
    @Async("webhookExecutor")
    public void processEventAsync(StravaWebhookEvent event) {
        try {
            switch (event.getAspectType()) {
                case "create":
                    handleActivityCreate(event);
                    break;
                case "update":
                    handleActivityUpdate(event);
                    break;
                case "delete":
                    handleActivityDelete(event);
                    break;
                default:
                    log.debug("Unhandled webhook event type: {}", event.getAspectType());
            }
        } catch (Exception e) {
            log.error("Failed to process Strava webhook event: {}", event, e);
            // Consider adding to dead letter queue for retry
        }
    }
    
    private void handleActivityCreate(StravaWebhookEvent event) {
        UUID atletaId = findAtletaByStravaId(event.getOwnerId());
        if (atletaId != null) {
            // Sync new activity
            stravaIntegrationService.syncSingleActivity(atletaId, event.getObjectId());
            
            // Trigger AI analysis update
            aiAnalysisService.scheduleAnalysisUpdate(atletaId);
        }
    }
}
```

---

## 📱 **GARMIN CONNECT IQ INTEGRATION**

### **Garmin OAuth Implementation**
```java
// Garmin uses different OAuth flow than Strava
@Service
public class GarminIntegrationService {
    
    @Value("${garmin.consumer-key}")
    private String consumerKey;
    
    @Value("${garmin.consumer-secret}")
    private String consumerSecret;
    
    // Garmin Health API uses OAuth 1.0a
    public String getGarminAuthUrl(UUID atletaId) {
        OAuthRequest request = new OAuthRequest(Verb.GET, 
            "https://connectapi.garmin.com/oauth-service/oauth/request_token");
        
        OAuth10aService service = new ServiceBuilder(consumerKey)
            .apiSecret(consumerSecret)
            .callback(getCallbackUrl())
            .build(GarminHealthApi.instance());
        
        service.signRequest(OAuth1RequestToken.empty(), request);
        
        Response response = service.execute(request);
        // Parse response and redirect to Garmin authorization
        
        return buildAuthorizationUrl(response.getBody());
    }
    
    // Garmin data is richer but requires more complex processing
    @Async
    public void syncGarminHealth(UUID atletaId) {
        GarminHealthData healthData = garminApiClient.getHealthData(atletaId);
        
        // Process different data types
        processHeartRateData(atletaId, healthData.getHeartRateData());
        processSleepData(atletaId, healthData.getSleepData());
        processStressData(atletaId, healthData.getStressData());
        processActivityData(atletaId, healthData.getActivities());
    }
}
```

---

## 🔍 **MONITORING & OBSERVABILITY**

### **Integration Health Monitoring**
```java
@Component
public class IntegrationHealthIndicator implements HealthIndicator {
    
    private final StravaAPIClient stravaClient;
    private final GarminAPIClient garminClient;
    
    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();
        
        try {
            // Test Strava connection
            boolean stravaHealthy = testStravaConnection();
            // Test Garmin connection  
            boolean garminHealthy = testGarminConnection();
            
            if (stravaHealthy && garminHealthy) {
                return builder.up()
                    .withDetail("strava", "UP")
                    .withDetail("garmin", "UP")
                    .build();
            } else {
                return builder.down()
                    .withDetail("strava", stravaHealthy ? "UP" : "DOWN")
                    .withDetail("garmin", garminHealthy ? "UP" : "DOWN")
                    .build();
            }
        } catch (Exception e) {
            return builder.down(e).build();
        }
    }
}

// Metrics para integrações
@Service
public class IntegrationMetricsService {
    
    private final MeterRegistry meterRegistry;
    private final Counter successfulSyncs;
    private final Counter failedSyncs;
    private final Timer syncDuration;
    
    public IntegrationMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.successfulSyncs = Counter.builder("integration.sync.success")
            .tag("type", "all")
            .register(meterRegistry);
        this.failedSyncs = Counter.builder("integration.sync.failed")
            .tag("type", "all") 
            .register(meterRegistry);
        this.syncDuration = Timer.builder("integration.sync.duration")
            .register(meterRegistry);
    }
    
    public void recordSuccessfulSync(IntegrationType type, Duration duration) {
        successfulSyncs.increment(Tags.of("integration_type", type.name()));
        Timer.Sample.start(meterRegistry).stop(syncDuration);
    }
}
```

---

## ⚡ **PERFORMANCE OPTIMIZATION**

### **Batch Processing & Parallel Sync**
```java
@Service
public class BatchSyncService {
    
    @Async("syncExecutor")
    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 5000))
    public void batchSyncUsers(List<UUID> userIds) {
        // Process users in parallel batches
        userIds.parallelStream()
            .collect(Collectors.groupingBy(id -> id.hashCode() % 5)) // 5 batches
            .values()
            .parallelStream()
            .forEach(batch -> {
                batch.forEach(this::syncUserData);
                // Rate limiting between batches
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
    }
    
    // Optimized data processing
    private void syncUserData(UUID userId) {
        CompletableFuture<List<StravaActivity>> stravaFuture = 
            stravaService.getActivitiesAsync(userId);
            
        CompletableFuture<List<GarminActivity>> garminFuture = 
            garminService.getActivitiesAsync(userId);
        
        // Process both simultaneously
        CompletableFuture.allOf(stravaFuture, garminFuture)
            .thenAccept(unused -> {
                try {
                    List<StravaActivity> stravaActivities = stravaFuture.join();
                    List<GarminActivity> garminActivities = garminFuture.join();
                    
                    // Merge and deduplicate activities
                    List<ImportedActivity> merged = mergeActivities(
                        userId, stravaActivities, garminActivities
                    );
                    
                    // Batch insert
                    batchInsertActivities(merged);
                    
                } catch (Exception e) {
                    log.error("Failed to sync user data: {}", userId, e);
                }
            });
    }
}
```

---

## 🛡️ **ERROR HANDLING & USER EXPERIENCE**

### **Graceful Error Handling**
```java
@ControllerAdvice
public class IntegrationExceptionHandler {
    
    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<?> handleTokenExpired(TokenExpiredException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of(
                "error", "token_expired",
                "message", "Please reconnect your " + e.getIntegrationType() + " account",
                "reauth_url", generateReauthUrl(e.getAtletaId(), e.getIntegrationType())
            ));
    }
    
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<?> handleRateLimit(RateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
            .body(Map.of(
                "error", "rate_limited",
                "message", "API rate limit exceeded. Data will sync automatically later.",
                "retry_after", e.getRetryAfterSeconds()
            ));
    }
}

// User-friendly integration status
@Service
public class IntegrationStatusService {
    
    public IntegrationStatus getIntegrationStatus(UUID atletaId) {
        return IntegrationStatus.builder()
            .stravaStatus(getStravaStatus(atletaId))
            .garminStatus(getGarminStatus(atletaId))
            .lastSyncAt(getLastSyncTime(atletaId))
            .nextSyncAt(calculateNextSyncTime(atletaId))
            .issuesCount(countActiveIssues(atletaId))
            .recommendations(generateRecommendations(atletaId))
            .build();
    }
    
    private ConnectionStatus getStravaStatus(UUID atletaId) {
        Optional<ExternalIntegration> integration = 
            integrationRepo.findByAtletaIdAndType(atletaId, IntegrationType.STRAVA);
        
        if (integration.isEmpty()) {
            return ConnectionStatus.NOT_CONNECTED;
        }
        
        if (integration.get().getTokenExpiry().isBefore(LocalDateTime.now())) {
            return ConnectionStatus.TOKEN_EXPIRED;
        }
        
        if (isRecentlySynced(integration.get())) {
            return ConnectionStatus.HEALTHY;
        }
        
        return ConnectionStatus.SYNC_ISSUES;
    }
}
```

---

## 📚 **DOCUMENTATION & TESTING**

### **Integration Testing Strategy**
```java
@TestConfiguration
public class IntegrationTestConfig {
    
    @Bean
    @Primary
    public StravaAPIClient mockStravaClient() {
        return Mockito.mock(StravaAPIClient.class);
    }
    
    @Bean
    @Primary  
    public GarminAPIClient mockGarminClient() {
        return Mockito.mock(GarminAPIClient.class);
    }
}

@SpringBootTest
@ActiveProfiles("test")
class StravaIntegrationServiceTest {
    
    @Autowired
    private StravaIntegrationService integrationService;
    
    @MockBean
    private StravaAPIClient stravaClient;
    
    @Test
    void shouldHandleSuccessfulSync() {
        // Given
        UUID atletaId = UUID.randomUUID();
        List<StravaActivity> mockActivities = createMockActivities();
        
        when(stravaClient.getActivities(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(mockActivities));
        
        // When
        integrationService.syncRecentActivities(atletaId);
        
        // Then
        verify(activityRepository, times(mockActivities.size()))
            .save(any(ImportedActivity.class));
    }
    
    @Test
    void shouldHandleApiFailureGracefully() {
        // Given
        UUID atletaId = UUID.randomUUID();
        
        when(stravaClient.getActivities(any(), any()))
            .thenReturn(CompletableFuture.failedFuture(
                new ResourceAccessException("Connection timeout")
            ));
        
        // When & Then
        assertDoesNotThrow(() -> 
            integrationService.syncRecentActivities(atletaId)
        );
        
        // Verify fallback behavior
        verify(cacheService).getCachedActivities(atletaId);
    }
}
```

---

## 🎯 **CHECKLIST DE IMPLEMENTAÇÃO**

### **Fase 1: Setup Básico**
- [ ] Registrar aplicação no Strava Developers
- [ ] Configurar OAuth endpoints seguros
- [ ] Implementar token encryption/decryption
- [ ] Criar entities para integrações
- [ ] Setup circuit breaker e retry logic

### **Fase 2: Core Integration**
- [ ] Implementar OAuth flow completo
- [ ] Desenvolver sync básico de atividades
- [ ] Configurar rate limiting
- [ ] Implementar cache strategy
- [ ] Testes de integração básicos

### **Fase 3: Production Ready**
- [ ] Webhook handlers para sync em tempo real
- [ ] Monitoring e health checks
- [ ] Error handling robusto
- [ ] User-friendly status/reconnect UX
- [ ] Performance optimization

### **Fase 4: Advanced Features**
- [ ] Batch processing otimizado
- [ ] Multiple integration support
- [ ] Data deduplication
- [ ] Advanced analytics
- [ ] Compliance e audit logs

---

## 🎉 **CONCLUSÃO**

### **Benefícios das Boas Práticas:**

1. **Segurança**: OAuth seguro, tokens criptografados
2. **Confiabilidade**: Circuit breakers, retry logic, fallbacks
3. **Performance**: Cache inteligente, batch processing, async
4. **Experiência**: Error handling graceful, status transparente
5. **Manutenibilidade**: Código limpo, testes abrangentes

### **Métricas de Sucesso:**
- **Uptime**: 99.9% disponibilidade das integrações
- **Sync Success Rate**: 95%+ sincronizações bem-sucedidas  
- **User Satisfaction**: <2% de tickets relacionados a integrações
- **Performance**: <5s para sincronizar 30 dias de atividades

**Com essas práticas, suas integrações serão robustas, seguras e proporcionarão uma excelente experiência ao usuário.** 🚀🔐✨

---

*Guia elaborado em 08/09/2025*  
*Última atualização: APIs Strava/Garmin 2025*