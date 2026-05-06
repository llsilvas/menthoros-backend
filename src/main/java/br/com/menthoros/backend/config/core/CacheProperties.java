package br.com.menthoros.backend.config.core;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {

    @NotNull(message = "defaultTtl cannot be null")
    private Duration defaultTtl = Duration.ofMinutes(30);

    @AssertTrue(message = "defaultTtl must be positive")
    public boolean isDefaultTtlPositive() {
        return defaultTtl != null && !defaultTtl.isNegative() && !defaultTtl.isZero();
    }

    @NotNull(message = "maximumSize cannot be null")
    @Min(value = 10, message = "maximumSize minimum is 10")
    private long maximumSize = 1000;

    @Valid
    private Map<String, CacheProfile> profiles = new HashMap<>();

    @Getter
    @Setter
    public static class CacheProfile {

        @NotNull(message = "ttl cannot be null")
        private Duration ttl;

        @AssertTrue(message = "ttl must be positive")
        public boolean isTtlPositive() {
            return ttl != null && !ttl.isNegative() && !ttl.isZero();
        }

        @Min(value = 1, message = "maxSize minimum is 1")
        private long maxSize = 100;

        public CacheProfile() {}

        public CacheProfile(Duration ttl, long maxSize) {
            this.ttl = ttl;
            this.maxSize = maxSize;
        }
    }
}
