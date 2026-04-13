package com.menthoros.config;

import com.menthoros.security.LocalTenantFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuração de segurança para o profile 'local'.
 *
 * ⚠️  ATENÇÃO: Este bean NUNCA deve ser ativado em produção ou CI.
 *     Desabilita autenticação JWT e usa um tenant fixo de desenvolvimento.
 *     Ativo apenas com: -Dspring.profiles.active=local
 *
 * Tem precedência sobre o SecurityConfig padrão via @Order(1).
 */
@Configuration
@Profile("local")
@RequiredArgsConstructor
@Order(1)
public class LocalSecurityConfig {

    private final LocalTenantFilter localTenantFilter;

    @Bean
    public SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(localTenantFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
