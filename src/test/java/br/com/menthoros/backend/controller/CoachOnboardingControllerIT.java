package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Conclusão do onboarding com segurança real. O slice roda com filtros desligados e não prova nada
 * sobre tenant nem sobre isolamento entre usuários; aqui o {@code JwtTenantFilter} está no caminho.
 *
 * <p>Autenticação por {@code jwt()}, nunca {@code @WithMockUser}: o filtro só popula o tenant
 * quando o principal é um {@code Jwt}, e o subject precisa ser UUID válido.
 */
@AutoConfigureMockMvc
@DisplayName("Conclusão do onboarding do coach — contrato HTTP com segurança real")
class CoachOnboardingControllerIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private Assessoria assessoriaA;
    private Assessoria assessoriaB;
    private UUID pendenteA;
    private UUID outroPendenteA;
    private UUID pendenteB;

    @BeforeEach
    void seed() {
        assessoriaA = seedAssessoria("Assessoria Onboarding A");
        assessoriaB = seedAssessoria("Assessoria Onboarding B");
        pendenteA = seedUsuario(assessoriaA, false);
        outroPendenteA = seedUsuario(assessoriaA, false);
        pendenteB = seedUsuario(assessoriaB, false);
    }

    @Nested
    @DisplayName("conclusão")
    class Conclusao {

        @Test
        @DisplayName("marca concluído e o me passa a refletir")
        void concluiEReflete() throws Exception {
            mockMvc.perform(get("/api/v1/users/me").with(jwtDe(pendenteA, assessoriaA.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.onboardingConcluido").value(false));

            mockMvc.perform(post("/api/v1/users/me/onboarding/concluir")
                            .with(jwtDe(pendenteA, assessoriaA.getId())))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/users/me").with(jwtDe(pendenteA, assessoriaA.getId())))
                    .andExpect(jsonPath("$.onboardingConcluido").value(true));
        }

        @Test
        @DisplayName("concluir duas vezes devolve 204 nas duas")
        void idempotente() throws Exception {
            for (int i = 0; i < 2; i++) {
                mockMvc.perform(post("/api/v1/users/me/onboarding/concluir")
                                .with(jwtDe(pendenteA, assessoriaA.getId())))
                        .andExpect(status().isNoContent());
            }

            assertThat(recarregar(pendenteA).isOnboardingConcluido()).isTrue();
        }
    }

    @Nested
    @DisplayName("isolamento")
    class Isolamento {

        /**
         * Concluir o próprio onboarding não pode concluir o de mais ninguém — nem de um colega da
         * mesma assessoria, nem de outro tenant. É o tipo de vazamento que passa despercebido
         * porque a resposta HTTP é a mesma.
         */
        @Test
        @DisplayName("concluir o meu não conclui o de outro usuário do mesmo tenant")
        void naoAfetaColegaDoMesmoTenant() throws Exception {
            mockMvc.perform(post("/api/v1/users/me/onboarding/concluir")
                            .with(jwtDe(pendenteA, assessoriaA.getId())))
                    .andExpect(status().isNoContent());

            assertThat(recarregar(outroPendenteA).isOnboardingConcluido()).isFalse();
        }

        @Test
        @DisplayName("concluir o meu não conclui o de outro tenant")
        void naoAfetaOutroTenant() throws Exception {
            mockMvc.perform(post("/api/v1/users/me/onboarding/concluir")
                            .with(jwtDe(pendenteA, assessoriaA.getId())))
                    .andExpect(status().isNoContent());

            assertThat(recarregar(pendenteB).isOnboardingConcluido()).isFalse();
        }

        /**
         * `sub` de um tenant com `tenant_id` de outro.
         *
         * <p>O request <b>nem chega</b> a este controller: o {@code LgpdConsentInterceptor} roda
         * antes, tenta resolver o usuário para avaliar consentimento, não o encontra naquele tenant
         * e devolve {@code 503} — "não consegui verificar" não é "não consentiu", decisão
         * deliberada daquela change. A consulta filtrada por {@code sub} e tenant no serviço é a
         * segunda barreira, não a primeira.
         *
         * <p>Por isso a asserção é sobre o <b>efeito</b>, não sobre o código exato: o que precisa
         * valer é que nenhuma escrita aconteça. Fixar {@code 503} aqui amarraria este teste a uma
         * decisão de outra camada, que pode mudar sem que este contrato mude.
         */
        @Test
        @DisplayName("sub de um tenant com tenant_id de outro não conclui nada")
        void tenantDivergente() throws Exception {
            mockMvc.perform(post("/api/v1/users/me/onboarding/concluir")
                            .with(jwtDe(pendenteA, assessoriaB.getId())))
                    .andExpect(status().is(not(equalTo(204))));

            assertThat(recarregar(pendenteA).isOnboardingConcluido())
                    .as("nenhuma escrita pode acontecer quando o tenant do token diverge")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("usuário legado nasce concluído e não precisa chamar nada")
    void usuarioLegadoNasceConcluido() throws Exception {
        UUID legado = seedUsuarioSemInformarOnboarding(assessoriaA);

        mockMvc.perform(get("/api/v1/users/me").with(jwtDe(legado, assessoriaA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingConcluido").value(true));
    }

    private RequestPostProcessor jwtDe(UUID subject, UUID tenantId) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_TECNICO"))
                .jwt(j -> j.subject(subject.toString()).claim("tenant_id", tenantId.toString()));
    }

    private Usuario recarregar(UUID id) {
        return usuarioRepository.findById(id).orElseThrow();
    }

    private Assessoria seedAssessoria(String nome) {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome(nome);
        assessoria.setDominio("onb-it-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        return assessoriaRepository.save(assessoria);
    }

    private UUID seedUsuario(Assessoria assessoria, boolean concluido) {
        UUID id = UUID.randomUUID();
        usuarioRepository.save(Usuario.builder()
                .id(id).keycloakId(id.toString())
                .nome("Coach").email("onb-" + id + "@exemplo.com")
                .role(UserRole.TECNICO).ativo(true)
                .onboardingConcluido(concluido)
                .assessoria(assessoria)
                .build());
        return id;
    }

    /** Sem passar o campo: exercita o default, que é o caminho de todo usuário anterior à V80. */
    private UUID seedUsuarioSemInformarOnboarding(Assessoria assessoria) {
        UUID id = UUID.randomUUID();
        usuarioRepository.save(Usuario.builder()
                .id(id).keycloakId(id.toString())
                .nome("Legado").email("legado-" + id + "@exemplo.com")
                .role(UserRole.TECNICO).ativo(true)
                .assessoria(assessoria)
                .build());
        return id;
    }
}
