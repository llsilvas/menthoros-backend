package com.menthoros.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class IaServiceConfig {

    @Value("${app.ia.service.strategy:original}")
    private String iaServiceStrategy;

    // Bean definitions removed - using @Component with @ConditionalOnProperty directly on implementations
}