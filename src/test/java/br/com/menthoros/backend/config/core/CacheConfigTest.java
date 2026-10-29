package br.com.menthoros.backend.config.core;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** D2 de garmin-connection: {@code garmin-pkce} tem TTL próprio de 10 min; os demais seguem o defaultTtl. */
class CacheConfigTest {

    private final CacheProperties properties = new CacheProperties();
    private final CacheManager cacheManager = new CacheConfig(properties).cacheManager();

    @Test
    @DisplayName("garmin-pkce existe com expireAfterWrite de 10 minutos")
    void garminPkceTemTtlProprio() {
        assertThat(ttl(CacheConfig.GARMIN_PKCE)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("atletas segue o defaultTtl")
    void atletasSegueDefault() {
        assertThat(ttl("atletas")).isEqualTo(properties.getDefaultTtl());
    }

    @Test
    @DisplayName("asMap().remove consome a entrada atomicamente (verifier não sobrevive ao callback)")
    void removeConsome() {
        Cache<Object, Object> nativo = nativo(CacheConfig.GARMIN_PKCE);
        nativo.put("state-1", "verifier-1");

        assertThat(nativo.asMap().remove("state-1")).isEqualTo("verifier-1");
        assertThat(nativo.asMap().remove("state-1")).isNull();
    }

    private Duration ttl(String nome) {
        long nanos = nativo(nome).policy().expireAfterWrite().orElseThrow().getExpiresAfter(TimeUnit.NANOSECONDS);
        return Duration.ofNanos(nanos);
    }

    @SuppressWarnings("unchecked")
    private Cache<Object, Object> nativo(String nome) {
        CaffeineCache cache = (CaffeineCache) cacheManager.getCache(nome);
        assertThat(cache).as("cache %s registrado", nome).isNotNull();
        return cache.getNativeCache();
    }
}
