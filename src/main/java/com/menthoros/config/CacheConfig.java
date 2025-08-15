package com.menthoros.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
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
public class CacheConfig {

    @Value("${app.cache.default-ttl:PT30M}")
    private Duration defaultTtl;

    @Value("${app.cache.maximum-size:1000}")
    private long maximumSize;

    @Bean
    @Primary
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // Configuração padrão para todos os caches
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(defaultTtl)
                .recordStats());

        // Configurar caches específicos
        cacheManager.setCacheNames(List.of(
                "atletas",           // Cache para atletas - TTL: 30min
                "atletas-list",      // Cache para lista de atletas - TTL: 30min
                "planos-semanais",   // Cache para planos semanais - TTL: 30min
                "embeddings",        // Cache para embeddings - TTL: 2h
                "ia-responses"       // Cache para respostas da IA - TTL: 1h
        ));

        return cacheManager;
    }




}