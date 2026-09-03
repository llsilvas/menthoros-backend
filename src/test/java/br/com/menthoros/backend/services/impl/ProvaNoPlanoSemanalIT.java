package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.dto.input.ProvaInputDto;
import br.com.menthoros.backend.dto.output.PlanoSemanalOutputDto;
import br.com.menthoros.backend.dto.output.ProvaOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.TipoProva;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.security.AuthenticatedAtletaResolver;
import br.com.menthoros.backend.services.PlanoService;
import br.com.menthoros.backend.services.ProvaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * D5 (prova-no-plano-semanal), ponta a ponta contra Postgres real: cadastro de prova na semana
 * corrente aprovada deixa o plano reaberto, e o {@code GET} do atleta continua devolvendo essa
 * versão — não a semana anterior (achado do levantamento, D4).
 *
 * <p>Deliberadamente SEM {@code @Transactional} na classe, mesmo padrão de
 * {@code AssessoriaLogoTransacaoIT}: cada chamada de serviço abre e fecha a própria transação, e
 * o estado é lido depois como a aplicação leria — o rollback automático de um teste transacional
 * esconderia justamente a reabertura sendo commitada de verdade.
 */
@DisplayName("prova-no-plano-semanal — cadastro de prova em semana já aprovada, ponta a ponta")
class ProvaNoPlanoSemanalIT extends AbstractIntegrationTest {

    @Autowired private ProvaService provaService;
    @Autowired private PlanoService planoService;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired private PlanoSemanalRepository planoSemanalRepository;

    /** Sem requisição HTTP não há JWT; TECNICO evita o subconjunto/validação do atleta. */
    @MockitoBean private AuthenticatedAtletaResolver atletaResolver;

    private UUID tenantId;
    private Atleta atleta;
    private PlanoSemanal planoAprovadoId;

    @BeforeEach
    void preparar() {
        when(atletaResolver.atuaComoAtleta()).thenReturn(false);

        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria IT Prova no Plano");
        assessoria.setDominio("prova-no-plano-it-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);
        tenantId = assessoria.getId();

        atleta = new Atleta();
        atleta.setNome("Atleta IT Prova no Plano");
        atleta.setEmail("prova-no-plano-it-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);

        planoAprovadoId = salvarPlanoAprovadoDaSemanaCorrente(atleta);

        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("cadastro na semana corrente aprovada reabre o plano e o PROVA aparece no GET do atleta")
    void cadastroReabreEAtletaVeOProva() {
        LocalDate dataProva = LocalDate.now().plusDays(2); // dentro da semana do plano aprovado
        ProvaInputDto input = new ProvaInputDto(
                "10K de teste", dataProva, TipoProva.CORRIDA_RUA, DistanciaProva.KM_10,
                null, false, null, Duration.ofMinutes(50), null, null,
                null, null, null, null, null, null, null, null, null);

        ProvaOutputDto provaCriada = provaService.criarProva(atleta.getId(), input);
        assertThat(provaCriada).isNotNull();

        // Só campos escalares — a coleção treinosPlanejados é lazy e esta classe é
        // deliberadamente sem @Transactional (ver javadoc da classe).
        PlanoSemanal planoRecarregado = planoSemanalRepository.findById(planoAprovadoId.getId()).orElseThrow();
        assertThat(planoRecarregado.getReviewStatus()).isEqualTo(PlanoReviewStatus.AGUARDANDO_REVISAO);
        assertThat(planoRecarregado.getMotivoReabertura().name()).isEqualTo("PROVA_INSERIDA");

        // buscarPlanoPorAtleta inicializa a coleção dentro da própria transação (@Transactional).
        PlanoSemanalOutputDto planoDoAtleta = planoService.buscarPlanoPorAtleta(atleta.getId(), true);
        assertThat(planoDoAtleta.id()).isEqualTo(planoAprovadoId.getId().toString());
        assertThat(planoDoAtleta.treinosPlanejados())
                .anyMatch(t -> "PROVA".equals(t.tipoTreino().name())
                        && dataProva.equals(t.dataTreino()));
    }

    private PlanoSemanal salvarPlanoAprovadoDaSemanaCorrente(Atleta atleta) {
        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(atleta.getAssessoria());
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        meta = planoMetadadosRepository.save(meta);

        LocalDate hoje = LocalDate.now();
        PlanoSemanal plano = new PlanoSemanal();
        plano.setAtleta(atleta);
        plano.setAssessoria(atleta.getAssessoria());
        plano.setPlanoMetaDados(meta);
        plano.setSemanaInicio(hoje.minusDays(1));
        plano.setSemanaFim(hoje.plusDays(5));
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(40));
        plano.setStatus(PlanoStatus.EM_ANDAMENTO);
        plano.setReviewStatus(PlanoReviewStatus.APROVADO);
        plano.setObjetivoSemanal("Semana de teste do IT");
        plano = planoSemanalRepository.save(plano);

        TreinoPlanejado longo = new TreinoPlanejado();
        longo.setPlanoSemanal(plano);
        longo.setAtleta(atleta);
        longo.setTenantId(atleta.getAssessoria().getId());
        longo.setDataTreino(hoje.plusDays(2));
        longo.setDiaSemana(DiaSemana.SABADO);
        longo.setTipoTreino(TipoTreino.LONGO);
        longo.setStatusTreino(TreinoExecucaoStatus.PENDENTE);
        longo.setDuracaoMin(Duration.ofMinutes(90));
        longo.setDistanciaKm(BigDecimal.valueOf(15.0));
        plano.setTreinosPlanejados(java.util.List.of(longo));
        return planoSemanalRepository.save(plano);
    }
}
