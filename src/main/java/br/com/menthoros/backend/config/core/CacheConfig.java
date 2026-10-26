package br.com.menthoros.backend.config.core;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig {

    private final CacheProperties cacheProperties;

    @Bean
    @Primary
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // Configuração padrão para todos os caches
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(cacheProperties.getMaximumSize())
                .expireAfterWrite(cacheProperties.getDefaultTtl())
                .recordStats());

        // Configurar caches específicos (todos usam o mesmo defaultTtl; TTL por cache não implementado)
        cacheManager.setCacheNames(List.of(
                "atletas",
                "atletas-list",
                "planos-semanais",
                "metadados-atleta",
                "embeddings",
                "ia-responses",
                "melhores-esforcos",
                "melhores-esforcos-atleta"
        ));

        // PKCE verifier do Garmin vive só até o callback: TTL próprio, igual ao do state (D2).
        cacheManager.registerCustomCache(GARMIN_PKCE, Caffeine.newBuilder()
                .maximumSize(cacheProperties.getMaximumSize())
                .expireAfterWrite(GARMIN_PKCE_TTL)
                .build());

        return cacheManager;
    }

    public static final String GARMIN_PKCE = "garmin-pkce";
    public static final Duration GARMIN_PKCE_TTL = Duration.ofMinutes(10);
}
