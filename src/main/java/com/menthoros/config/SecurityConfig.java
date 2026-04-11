package com.menthoros.config;

import com.menthoros.security.JwtTenantFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTenantFilter jwtTenantFilter;

    @Value("${app.security.roles-client-id:menthoros-api}")
    private String rolesClientId;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults()) // usa o CorsConfigurationSource bean do CorsConfig
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**", "/swagger-ui/**", "/api-docs/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                )
                .addFilterAfter(jwtTenantFilter, org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter());
        return jwtAuthenticationConverter;
    }

    @Bean
    public Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthoritiesConverter() {
        return jwt -> extractRoles(jwt).stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @SuppressWarnings("unchecked")
    private Collection<String> extractRoles(Jwt jwt) {
        Set<String> roles = new LinkedHashSet<>();
        Object flatRoles = jwt.getClaim("roles");

        if (flatRoles instanceof List<?>) {
            for (Object role : (List<?>) flatRoles) {
                if (role instanceof String roleName && !roleName.isBlank()) {
                    roles.add(roleName);
                }
            }
        } else if (flatRoles instanceof String role && !role.isBlank()) {
            roles.add(role);
        }

        Object resourceAccessClaim = jwt.getClaim("resource_access");
        if (resourceAccessClaim instanceof Map<?, ?> resourceAccessMap) {
            Object clientAccess = resourceAccessMap.get(rolesClientId);
            if (clientAccess instanceof Map<?, ?> clientAccessMap) {
                Object clientRoles = clientAccessMap.get("roles");
                if (clientRoles instanceof List<?>) {
                    for (Object role : (List<?>) clientRoles) {
                        if (role instanceof String roleName && !roleName.isBlank()) {
                            roles.add(roleName);
                        }
                    }
                }
            }
        }

        return roles;
    }
}
