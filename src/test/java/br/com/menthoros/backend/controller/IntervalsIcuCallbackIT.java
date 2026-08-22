package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.helper.IntervalsIcuStateSigner;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Callback OAuth2 do intervals.icu contra o contexto real: cadeia de segurança de verdade
 * ({@code permitAll} via {@code intervalsIcuPaths}), {@code JwtTenantFilter}, Flyway/Postgres.
 *
 * <p><b>Por que um {@code *IT} e não só o slice:</b> o {@code @WebMvcTest} mocka o service, então
 * ele prova o contrato HTTP mas não que a linha chega ao banco com o tenant certo — nem que nada
 * é gravado nos caminhos de falha. Aqui a asserção é sobre a tabela, que é onde o estrago de CA12
 * aconteceria.
 *
 * <p>Sem JWT em nenhum teste, de propósito: é assim que o provedor chama este endpoint.
 */
@AutoConfigureMockMvc
@DisplayName("GET /api/v1/integracoes/intervals-icu/callback")
class IntervalsIcuCallbackIT extends AbstractIntegrationTest {

    private static final String CALLBACK = "/api/v1/integracoes/intervals-icu/callback";
    private static final String EXTERNAL_ATHLETE_ID = "i641775";

    private static WireMockServer provedor;

    @BeforeAll
    static void iniciarProvedor() {
        provedor = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        provedor.start();
    }

    @AfterAll
    static void pararProvedor() {
        provedor.stop();
    }

    // O token-uri real aponta para https://intervals.icu. Sem este override o teste sairia para a
    // internet — lento, instável e dependente de credencial real.
    @DynamicPropertySource
    static void apontarTokenUriParaWireMock(DynamicPropertyRegistry registry) {
        registry.add("app.intervals-icu.token-uri", () -> provedor.baseUrl() + "/api/oauth/token");
        registry.add("app.intervals-icu.base-url", provedor::baseUrl);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private IntegracaoExternaRepository integracaoRepository;
    @Autowired private IntervalsIcuStateSigner stateSigner;

    private Assessoria assessoria;
    private UUID atletaId;

    @BeforeEach
    void seed() {
        provedor.resetAll();
        integracaoRepository.deleteAll();
        assessoria = seedAssessoria();
        atletaId = seedAtleta(assessoria);
    }

    private Assessoria seedAssessoria() {
        Assessoria a = new Assessoria();
        a.setNome("Assessoria Callback");
        a.setDominio("cb-" + UUID.randomUUID());
        a.setPlano(PlanoAssessoria.BASIC);
        return assessoriaRepository.save(a);
    }

    private UUID seedAtleta(Assessoria a) {
        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Callback");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setEmail("cb-" + UUID.randomUUID() + "@exemplo.com");
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(a);
        return atletaRepository.save(atleta).getId();
    }

    private void stubTokenOk() {
        provedor.stubFor(post(urlEqualTo("/api/oauth/token"))
                .willReturn(okJson("""
                        {"token_type":"Bearer","access_token":"tok-real",
                         "scope":"ACTIVITY:READ,CALENDAR:WRITE",
                         "athlete":{"id":"%s","name":"Leandro"}}
                        """.formatted(EXTERNAL_ATHLETE_ID))));
    }

    private Optional<IntegracaoExterna> conexaoDoAtleta() {
        return integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(
                atletaId, FonteDados.INTERVALS_ICU, assessoria.getId());
    }

    private String locationDe(MvcResult resultado) {
        return resultado.getResponse().getHeader("Location");
    }

    @Nested
    @DisplayName("sucesso")
    class Sucesso {

        @Test
        @DisplayName("persiste a conexão com tenant do atleta e redireciona com success")
        void persisteERedireciona() throws Exception {
            stubTokenOk();

            MvcResult resultado = mockMvc.perform(get(CALLBACK)
                            .param("code", "code-valido")
                            .param("state", stateSigner.assinar(atletaId)))
                    .andExpect(status().isFound())
                    .andReturn();

            assertThat(locationDe(resultado)).contains("intervals-icu=success");

            IntegracaoExterna conexao = conexaoDoAtleta().orElseThrow();
            assertThat(conexao.getAccessToken()).isEqualTo("tok-real");
            assertThat(conexao.getScopes()).isEqualTo("ACTIVITY:READ,CALENDAR:WRITE");
            assertThat(conexao.getExternalAthleteId()).isEqualTo(EXTERNAL_ATHLETE_ID);
            assertThat(conexao.isAtivo()).isTrue();
            // CA9 — o callback não tem JWT; o tenant só pode vir do atleta resolvido pelo state.
            assertThat(conexao.getTenantId()).isEqualTo(assessoria.getId());
            // D3 — o provedor não emite nenhum dos dois.
            assertThat(conexao.getRefreshToken()).isNull();
            assertThat(conexao.getTokenExpiraEm()).isNull();
        }

        @Test
        @DisplayName("o Bearer do token trocado é o que vai nas chamadas seguintes")
        void tokenPersistidoEhODoProvedor() throws Exception {
            stubTokenOk();

            mockMvc.perform(get(CALLBACK)
                    .param("code", "code-valido")
                    .param("state", stateSigner.assinar(atletaId)));

            assertThat(conexaoDoAtleta().orElseThrow().getAccessToken()).isEqualTo("tok-real");
        }
    }

    @Nested
    @DisplayName("nadaPersistido")
    class NadaPersistido {

        @Test
        @DisplayName("?error=access_denied — nada gravado")
        void erroDoProvedor() throws Exception {
            MvcResult resultado = mockMvc.perform(get(CALLBACK).param("error", "access_denied"))
                    .andExpect(status().isFound())
                    .andReturn();

            assertThat(locationDe(resultado)).contains("intervals-icu=error");
            assertThat(conexaoDoAtleta()).isEmpty();
        }

        @Test
        @DisplayName("state adulterado — nada gravado")
        void stateAdulterado() throws Exception {
            String state = stateSigner.assinar(atletaId);
            String[] partes = state.split("\\.");
            String adulterado = partes[0] + "." + partes[1] + "." + partes[2].substring(1) + "X";

            MvcResult resultado = mockMvc.perform(get(CALLBACK)
                            .param("code", "code-valido")
                            .param("state", adulterado))
                    .andExpect(status().isFound())
                    .andReturn();

            assertThat(locationDe(resultado)).contains("intervals-icu=error");
            assertThat(conexaoDoAtleta()).isEmpty();
        }

        // O ataque de D2: trocar o atletaId mantendo assinatura legítima de outro state.
        @Test
        @DisplayName("atletaId trocado no state — nada gravado para nenhum dos dois atletas")
        void atletaIdTrocado() throws Exception {
            UUID outroAtletaId = seedAtleta(assessoria);
            String state = stateSigner.assinar(outroAtletaId);
            String[] partes = state.split("\\.");
            String forjado = atletaId + "." + partes[1] + "." + partes[2];

            mockMvc.perform(get(CALLBACK).param("code", "code-valido").param("state", forjado))
                    .andExpect(status().isFound());

            assertThat(conexaoDoAtleta()).isEmpty();
            assertThat(integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(
                    outroAtletaId, FonteDados.INTERVALS_ICU, assessoria.getId())).isEmpty();
        }

        @Test
        @DisplayName("state malformado (não-UUID) — redireciona, não é 400")
        void stateMalformado() throws Exception {
            MvcResult resultado = mockMvc.perform(get(CALLBACK)
                            .param("code", "code-valido")
                            .param("state", "isto-nao-e-um-state"))
                    .andExpect(status().isFound())
                    .andReturn();

            assertThat(locationDe(resultado)).contains("intervals-icu=error");
            assertThat(conexaoDoAtleta()).isEmpty();
        }

        @Test
        @DisplayName("sem code nem state — redireciona, não é 400")
        void semParametros() throws Exception {
            mockMvc.perform(get(CALLBACK))
                    .andExpect(status().isFound());

            assertThat(conexaoDoAtleta()).isEmpty();
        }

        @Test
        @DisplayName("provedor recusa o code — nada gravado")
        void provedorRecusaCode() throws Exception {
            provedor.stubFor(post(urlEqualTo("/api/oauth/token"))
                    .willReturn(aResponse().withStatus(400).withBody("invalid_grant")));

            MvcResult resultado = mockMvc.perform(get(CALLBACK)
                            .param("code", "code-expirado")
                            .param("state", stateSigner.assinar(atletaId)))
                    .andExpect(status().isFound())
                    .andReturn();

            assertThat(locationDe(resultado)).contains("intervals-icu=error");
            assertThat(conexaoDoAtleta()).isEmpty();
        }
    }

    @Nested
    @DisplayName("guardContaJaVinculada")
    class GuardContaJaVinculada {

        // CA12 contra o banco de verdade. É aqui que o achado JPA aparece ou não: se o guard
        // rodasse depois do find-or-create, a entidade managed seria gravada no flush do commit
        // mesmo sem save() explícito, e este teste falharia.
        @Test
        @DisplayName("conta já vinculada a outro atleta do tenant — nada gravado para o segundo")
        void contaJaVinculadaNaoGrava() throws Exception {
            UUID primeiroAtletaId = seedAtleta(assessoria);
            IntegracaoExterna existente = new IntegracaoExterna();
            existente.setAtleta(atletaRepository.findById(primeiroAtletaId).orElseThrow());
            existente.setPlataforma(FonteDados.INTERVALS_ICU);
            existente.setTenantId(assessoria.getId());
            existente.setExternalAthleteId(EXTERNAL_ATHLETE_ID);
            existente.setAccessToken("tok-do-primeiro");
            existente.setAtivo(true);
            integracaoRepository.save(existente);

            stubTokenOk();

            MvcResult resultado = mockMvc.perform(get(CALLBACK)
                            .param("code", "code-valido")
                            .param("state", stateSigner.assinar(atletaId)))
                    .andExpect(status().isFound())
                    .andReturn();

            assertThat(locationDe(resultado)).contains("intervals-icu=error");
            // O segundo atleta não ficou com conexão...
            assertThat(conexaoDoAtleta()).isEmpty();
            // ...e a do primeiro seguiu intacta.
            IntegracaoExterna doPrimeiro = integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(
                    primeiroAtletaId, FonteDados.INTERVALS_ICU, assessoria.getId()).orElseThrow();
            assertThat(doPrimeiro.getAccessToken()).isEqualTo("tok-do-primeiro");
            assertThat(doPrimeiro.isAtivo()).isTrue();
        }

        @Test
        @DisplayName("reconexão do próprio atleta é permitida e atualiza o token")
        void reconexaoDoProprioAtleta() throws Exception {
            IntegracaoExterna antiga = new IntegracaoExterna();
            antiga.setAtleta(atletaRepository.findById(atletaId).orElseThrow());
            antiga.setPlataforma(FonteDados.INTERVALS_ICU);
            antiga.setTenantId(assessoria.getId());
            antiga.setExternalAthleteId(EXTERNAL_ATHLETE_ID);
            antiga.setAccessToken("tok-antigo");
            antiga.setAtivo(true);
            integracaoRepository.save(antiga);

            stubTokenOk();

            MvcResult resultado = mockMvc.perform(get(CALLBACK)
                            .param("code", "code-valido")
                            .param("state", stateSigner.assinar(atletaId)))
                    .andExpect(status().isFound())
                    .andReturn();

            assertThat(locationDe(resultado)).contains("intervals-icu=success");
            assertThat(conexaoDoAtleta().orElseThrow().getAccessToken()).isEqualTo("tok-real");
        }
    }

    @Nested
    @DisplayName("naoVazaCredencial")
    class NaoVazaCredencial {

        // CA10 — a URL de redirect fica na barra do browser e no histórico.
        @Test
        @DisplayName("nem o code nem o state aparecem na URL de redirect")
        void redirectNaoCarregaCredencial() throws Exception {
            stubTokenOk();
            String state = stateSigner.assinar(atletaId);

            MvcResult resultado = mockMvc.perform(get(CALLBACK)
                            .param("code", "code-secreto-do-provedor")
                            .param("state", state))
                    .andReturn();

            assertThat(locationDe(resultado))
                    .doesNotContain("code-secreto-do-provedor")
                    .doesNotContain(state)
                    .doesNotContain("tok-real");
        }
    }
}
