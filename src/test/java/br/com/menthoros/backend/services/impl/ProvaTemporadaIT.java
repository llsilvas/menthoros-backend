package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoTreino;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.SeasonPlanStatus;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.SeasonPlanFixtures;
import br.com.menthoros.backend.security.AuthenticatedAtletaResolver;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import br.com.menthoros.backend.services.ProvaService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Métrica do ticket add-season-plan-model-integrity: zero linhas apagadas em cascata a partir da
 * prova-alvo. Ponta a ponta contra Postgres real (V116/V117).
 *
 * <p>Deliberadamente SEM {@code @Transactional} na classe, mesmo padrão de {@code ProvaNoPlanoSemanalIT}:
 * cada chamada de serviço commita de verdade, e as FKs são exercitadas no commit, não escondidas
 * por rollback.
 */
@DisplayName("plano de temporada — excluir ou cancelar a prova-alvo não apaga histórico")
class ProvaTemporadaIT extends AbstractIntegrationTest {

    @Autowired private ProvaService provaService;
    @Autowired private EntityManager em;
    @Autowired private TransactionTemplate tx;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private AuthenticatedAtletaResolver atletaResolver;
    @MockitoBean private AuthenticatedPrincipalResolver principalResolver;

    private Atleta atleta;
    private Prova prova;
    private PlanoTreino temporada;
    private PlanoTreino proposta;

    @BeforeEach
    void preparar() {
        when(atletaResolver.atuaComoAtleta()).thenReturn(false);
        tx.executeWithoutResult(s -> {
            SeasonPlanFixtures fx = new SeasonPlanFixtures(em);
            atleta = fx.atleta(fx.assessoria());
            prova = fx.prova(atleta);
            temporada = fx.planoTreino(atleta, prova, SeasonPlanStatus.ACTIVE);
            proposta = fx.planoTreino(atleta, prova, SeasonPlanStatus.PROPOSED);
            fx.semana(temporada, 1);
            fx.semana(temporada, 2);
            fx.planoSemanalAprovado(temporada);
        });
        TenantContext.setTenantId(atleta.getAssessoria().getId());
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("ADMIN exclui prova com temporada: 422 e nenhuma linha apagada")
    void adminExcluirRecusaSemApagar() {
        when(principalResolver.hasRole(UserRole.ADMIN)).thenReturn(true);

        assertThatThrownBy(() -> provaService.removerProva(atleta.getId(), prova.getId()))
                .isInstanceOf(DomainRuleViolationException.class);

        assertThat(contar("tb_prova", "id", prova.getId())).isEqualTo(1);
        assertThat(contar("tb_plano_treino", "prova_id", prova.getId())).isEqualTo(2);
        assertThat(contar("tb_season_week", "season_plan_id", temporada.getId())).isEqualTo(2);
        assertThat(contar("tb_plano_semanal", "plano_treino_id", temporada.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("treinador cancela pelo DELETE: temporada e proposta arquivadas, nenhuma linha apagada")
    void treinadorCancelaArquivaSemApagar() {
        when(principalResolver.hasRole(UserRole.ADMIN)).thenReturn(false);

        provaService.removerProva(atleta.getId(), prova.getId());

        assertThat(status(temporada)).isEqualTo("ARCHIVED");
        assertThat(status(proposta)).isEqualTo("ARCHIVED");
        assertThat(jdbc.queryForObject("select version_reason from tb_plano_treino where id = ?",
                String.class, temporada.getId())).isEqualTo("prova cancelada");
        assertThat(contar("tb_season_week", "season_plan_id", temporada.getId())).isEqualTo(2);
        assertThat(contar("tb_plano_semanal", "plano_treino_id", temporada.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("ADMIN exclui prova referenciada só como prova_alvo_id legado: 422, não erro de constraint")
    void adminExcluirProvaAlvoLegado() {
        Prova[] alvoLegado = new Prova[1];
        tx.executeWithoutResult(s -> {
            SeasonPlanFixtures fx = new SeasonPlanFixtures(em);
            Atleta gerenciado = em.find(Atleta.class, atleta.getId());
            alvoLegado[0] = fx.prova(gerenciado);
            PlanoTreino legado = fx.planoTreino(gerenciado, fx.prova(gerenciado), null);
            legado.setProvaAlvo(alvoLegado[0]);
        });
        when(principalResolver.hasRole(UserRole.ADMIN)).thenReturn(true);

        assertThatThrownBy(() -> provaService.removerProva(atleta.getId(), alvoLegado[0].getId()))
                .isInstanceOf(DomainRuleViolationException.class);

        assertThat(contar("tb_prova", "id", alvoLegado[0].getId())).isEqualTo(1);
    }

    private String status(PlanoTreino plano) {
        return jdbc.queryForObject("select season_status from tb_plano_treino where id = ?",
                String.class, plano.getId());
    }

    private int contar(String tabela, String coluna, UUID valor) {
        Integer n = jdbc.queryForObject(
                "select count(*) from " + tabela + " where " + coluna + " = ?", Integer.class, valor);
        return n == null ? 0 : n;
    }
}
