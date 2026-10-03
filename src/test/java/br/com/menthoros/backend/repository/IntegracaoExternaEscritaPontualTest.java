package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escrita pontual em {@code tb_integracao_externa} (fix-sync-cursor-data-loss, design D0/D1).
 *
 * <p>Sem {@code @Transactional} na classe, de propósito: os schedulers chamam esses métodos sem
 * transação própria, e um {@code @Modifying} sem transação lançaria
 * {@code TransactionRequiredException}. Cada teste usa assessoria/atleta novos, então não há
 * interferência entre eles sem rollback.</p>
 */
@DisplayName("IntegracaoExternaRepository: pull_cursor e status por UPDATE pontual")
class IntegracaoExternaEscritaPontualTest extends AbstractIntegrationTest {

    @Autowired
    private AtletaRepository atletaRepository;
    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private IntegracaoExternaRepository repository;

    @Test
    @DisplayName("atualizarPullCursor funciona sem transação do chamador")
    void atualizaCursorSemTransacao() {
        IntegracaoExterna integracao = seedIntegracao();
        Instant cursor = Instant.parse("2026-09-10T06:00:00Z");

        int linhas = repository.atualizarPullCursor(integracao.getId(), integracao.getTenantId(), cursor);

        assertThat(linhas).isEqualTo(1);
        assertThat(recarregar(integracao).getPullCursor()).isEqualTo(cursor);
    }

    @Test
    @DisplayName("CA9 — tenant errado não altera nenhuma linha")
    void tenantErradoNaoAltera() {
        IntegracaoExterna integracao = seedIntegracao();

        int linhas = repository.atualizarPullCursor(integracao.getId(), UUID.randomUUID(), Instant.now());

        assertThat(linhas).isZero();
        assertThat(recarregar(integracao).getPullCursor()).isNull();
    }

    @Test
    @DisplayName("CA1 — save de uma instância antiga (push, webhook) não reescreve pull_cursor")
    void saveDeInstanciaAntigaNaoReescreveCursor() {
        IntegracaoExterna integracao = seedIntegracao();
        IntegracaoExterna antiga = recarregar(integracao);
        Instant cursor = Instant.parse("2026-09-10T06:00:00Z");
        repository.atualizarPullCursor(integracao.getId(), integracao.getTenantId(), cursor);

        antiga.setUltimaSincronizacao(Instant.now());
        repository.save(antiga);

        assertThat(recarregar(integracao).getPullCursor()).isEqualTo(cursor);
    }

    @Test
    @DisplayName("atualizarStatusSync grava só o status e preserva um token renovado depois da leitura")
    void statusNaoApagaTokenRenovado() {
        IntegracaoExterna integracao = seedIntegracao();
        IntegracaoExterna renovada = recarregar(integracao);
        renovada.setAccessToken("token-renovado");
        repository.save(renovada);
        Instant agora = Instant.parse("2026-09-30T12:00:00Z");

        int linhas = repository.atualizarStatusSync(
                integracao.getId(), integracao.getTenantId(), agora, 7, "parcial");

        IntegracaoExterna depois = recarregar(integracao);
        assertThat(linhas).isEqualTo(1);
        assertThat(depois.getUltimaSincronizacao()).isEqualTo(agora);
        assertThat(depois.getSyncActivityCount()).isEqualTo(7);
        assertThat(depois.getLastSyncError()).isEqualTo("parcial");
        assertThat(depois.getAccessToken()).isEqualTo("token-renovado");
    }

    @Test
    @DisplayName("atualizarStatusSync com tenant errado não altera nenhuma linha")
    void statusTenantErrado() {
        IntegracaoExterna integracao = seedIntegracao();

        assertThat(repository.atualizarStatusSync(
                integracao.getId(), UUID.randomUUID(), Instant.now(), 1, null)).isZero();
    }

    private IntegracaoExterna recarregar(IntegracaoExterna integracao) {
        return repository.findById(integracao.getId()).orElseThrow();
    }

    private IntegracaoExterna seedIntegracao() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Escrita Pontual");
        assessoria.setDominio("escrita-pontual-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Escrita Pontual");
        atleta.setEmail("escrita-pontual-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);

        IntegracaoExterna integracao = new IntegracaoExterna();
        integracao.setAtleta(atleta);
        integracao.setTenantId(assessoria.getId());
        integracao.setPlataforma(FonteDados.STRAVA);
        integracao.setAtivo(true);
        integracao.setAccessToken("token-original");
        return repository.save(integracao);
    }
}
