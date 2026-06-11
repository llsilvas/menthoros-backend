package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.stream.Stream;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioSyncServiceImplRoleTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AssessoriaRepository assessoriaRepository;

    @InjectMocks private UsuarioSyncServiceImpl service;

    private Jwt jwtComRole(String role) {
        return jwtComRoles(List.of(role));
    }

    private Jwt jwtComRoles(List<String> roles) {
        return Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("email", "atleta@teste.com")
                .claim("given_name", "Ana")
                .claim("family_name", "Atleta")
                .claim("email_verified", true)
                .claim("realm_access", Map.of("roles", roles))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    private void mockTenantECriacao(UUID tenantId) {
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        when(assessoriaRepository.findById(tenantId)).thenReturn(Optional.of(assessoria));
        when(usuarioRepository.findByKeycloakId(any())).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void deveMapearRoleAtleta() {
        UUID tenantId = UUID.randomUUID();
        mockTenantECriacao(tenantId);

        Usuario usuario = service.syncUsuarioFromJwt(jwtComRole("ATLETA"), tenantId);

        assertThat(usuario.getRole()).isEqualTo(UserRole.ATLETA);
    }

    static Stream<Arguments> rolesEPrioridade() {
        return Stream.of(
                Arguments.of(List.of("ATLETA", "ADMIN"), UserRole.ADMIN),
                Arguments.of(List.of("ATLETA", "TECNICO"), UserRole.TECNICO),
                Arguments.of(List.of("ATLETA", "VISUALIZADOR"), UserRole.ATLETA),
                Arguments.of(List.of(), UserRole.VISUALIZADOR)
        );
    }

    @ParameterizedTest
    @MethodSource("rolesEPrioridade")
    void deveResolverRolePorPrioridade(List<String> roles, UserRole esperada) {
        UUID tenantId = UUID.randomUUID();
        mockTenantECriacao(tenantId);

        Usuario usuario = service.syncUsuarioFromJwt(jwtComRoles(roles), tenantId);

        assertThat(usuario.getRole()).isEqualTo(esperada);
    }
}
