package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioSyncServiceImplRoleTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AssessoriaRepository assessoriaRepository;
    @Mock private AtletaRepository atletaRepository;

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
        // Vínculo Usuario↔Atleta roda apenas para role ATLETA; lenient evita UnnecessaryStubbing nos demais casos.
        lenient().when(atletaRepository.findByEmailAndAssessoria_Id(any(), any())).thenReturn(Optional.empty());
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
                Arguments.of(List.of(), UserRole.VISUALIZADOR),
                // PROPRIETARIO é composite de TECNICO: o token traz as duas e a role
                // resolvida tem de continuar TECNICO. Ver o bloco abaixo para o porquê.
                Arguments.of(List.of("PROPRIETARIO", "TECNICO"), UserRole.TECNICO)
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

    /**
     * `Usuario.role` guarda um único valor. Se `PROPRIETARIO` entrasse na cadeia de
     * `mapToUserRole`, o dono deixaria de ser contado como técnico — e quem conta é
     * `countByTenantIdAndRoleAndAtivoTrue`, com `maxTecnicos = 1` no plano BASIC.
     * A propriedade vive na flag `owner`, não na role. Este teste é a rede disso.
     */
    @Test
    void proprietarioContinuaSendoTecnicoNaRole() {
        UUID tenantId = UUID.randomUUID();
        mockTenantECriacao(tenantId);

        Usuario usuario = service.syncUsuarioFromJwt(
                jwtComRoles(List.of("PROPRIETARIO", "TECNICO")), tenantId);

        assertThat(usuario.getRole()).isEqualTo(UserRole.TECNICO);
        assertThat(usuario.isTecnico()).isTrue();
        assertThat(usuario.podeEscrever()).isTrue();
    }

    /**
     * O sync roda a cada requisição. Se ele escrevesse `onboardingConcluido`, reabriria o wizard
     * para quem já concluiu — ou, pior, criaria todo usuário sincronizado como pendente.
     */
    @Test
    @DisplayName("o sync não mexe no estado de onboarding")
    void syncNaoTocaOnboarding() {
        UUID tenantId = UUID.randomUUID();
        Usuario existente = new Usuario();
        existente.setId(UUID.randomUUID());
        existente.setKeycloakId(existente.getId().toString());
        existente.setOnboardingConcluido(false);
        when(usuarioRepository.findByKeycloakId(any())).thenReturn(Optional.of(existente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario usuario = service.syncUsuarioFromJwt(jwtComRole("TECNICO"), tenantId);

        assertThat(usuario.isOnboardingConcluido())
                .as("quem estava pendente continua pendente; o sync não decide isso")
                .isFalse();
    }

    @Test
    @DisplayName("usuário novo criado pelo sync nasce concluído")
    void usuarioNovoDoSyncNasceConcluido() {
        UUID tenantId = UUID.randomUUID();
        mockTenantECriacao(tenantId);

        Usuario usuario = service.syncUsuarioFromJwt(jwtComRole("TECNICO"), tenantId);

        assertThat(usuario.isOnboardingConcluido())
                .as("o sync cria usuário a cada primeiro acesso; nenhum deles deve ver o wizard")
                .isTrue();
    }

    @Nested
    @DisplayName("espelho da flag owner")
    class EspelhoDaFlagOwner {

        @Test
        @DisplayName("liga a flag quando o token traz PROPRIETARIO")
        void ligaQuandoTokenTrazRole() {
            UUID tenantId = UUID.randomUUID();
            mockTenantECriacao(tenantId);

            Usuario usuario = service.syncUsuarioFromJwt(
                    jwtComRoles(List.of("PROPRIETARIO", "TECNICO")), tenantId);

            assertThat(usuario.isOwner()).isTrue();
        }

        @Test
        @DisplayName("mantém a flag desligada para técnico comum")
        void naoLigaParaTecnicoComum() {
            UUID tenantId = UUID.randomUUID();
            mockTenantECriacao(tenantId);

            Usuario usuario = service.syncUsuarioFromJwt(jwtComRole("TECNICO"), tenantId);

            assertThat(usuario.isOwner()).isFalse();
        }

        /**
         * A flag é espelho, não segunda fonte da verdade: perder a role no Keycloak tem de
         * desligá-la no próximo acesso. Sem este caso, um dono removido no IdP continuaria
         * dono no banco para sempre.
         */
        @Test
        @DisplayName("desliga a flag quando a role some do token")
        void desligaQuandoRoleSomeDoToken() {
            UUID tenantId = UUID.randomUUID();
            Usuario existente = new Usuario();
            existente.setId(UUID.randomUUID());
            existente.setKeycloakId(existente.getId().toString());
            existente.setOwner(true);
            when(usuarioRepository.findByKeycloakId(any())).thenReturn(Optional.of(existente));
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

            Usuario usuario = service.syncUsuarioFromJwt(jwtComRole("TECNICO"), tenantId);

            assertThat(usuario.isOwner()).isFalse();
        }
    }
}
