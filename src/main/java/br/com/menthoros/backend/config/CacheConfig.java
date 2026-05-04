package br.com.menthoros.backend.config;

import br.com.menthoros.backend.config.core.CacheProperties;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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

        // Configurar caches específicos
        cacheManager.setCacheNames(List.of(
                "atletas",           // Cache para atletas - TTL: 30min
                "atletas-list",      // Cache para lista de atletas - TTL: 30min
                "planos-semanais",   // Cache para planos semanais - TTL: 30min
                "metadados-atleta",  // Cache para metadados de plano por atleta - TTL: 30min
                "embeddings",        // Cache para embeddings - TTL: 2h
                "ia-responses"       // Cache para respostas da IA - TTL: 1h
        ));

        return cacheManager;
    }
}