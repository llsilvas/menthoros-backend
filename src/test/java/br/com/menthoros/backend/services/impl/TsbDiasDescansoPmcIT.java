package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.dto.output.PmcPontoDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.TsbService;
import br.com.menthoros.backend.services.helper.TssCalculatorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * fix-tsb-rest-days-materialization, CA1 e CA7 contra Postgres real: treino ingerido depois de dias
 * sem métrica materializa os dias de descanso com decaimento, e o PMC do atleta sai com um ponto por
 * dia — sem o buraco que o gráfico mostrava como "Sem dado no período".
 */
class TsbDiasDescansoPmcIT extends AbstractIntegrationTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired private TsbService tsbService;
    @Autowired private AtletaProgressService atletaProgressService;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired private TreinoRealizadoRepository treinoRealizadoRepository;
    @Autowired private MetricasDiariasRepository metricasDiariasRepository;
    @Autowired private TssCalculatorService tssCalculatorService;

    @AfterEach
    void limpaTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("CA1: ingestão em D0+5 cria D0+1..D0+5, com TSS 0 e CTL decrescente nos dias de descanso")
    void ingestaoAposDiasDeDescansoMaterializaODecaimento() {
        LocalDate d0 = LocalDate.now(SP).minusDays(10);
        Atleta atleta = cenario(d0);

        for (int i = 1; i <= 5; i++) {
            assertThat(metricasDiariasRepository.findByAtletaIdAndData(atleta.getId(), d0.plusDays(i)))
                    .as("linha de D0+%d", i).isPresent();
        }
        double ctlD0 = ctl(atleta, d0);
        double ctlD1 = ctl(atleta, d0.plusDays(1));
        double ctlD2 = ctl(atleta, d0.plusDays(2));
        double ctlD3 = ctl(atleta, d0.plusDays(3));
        double ctlD4 = ctl(atleta, d0.plusDays(4));
        double ctlD5 = ctl(atleta, d0.plusDays(5));

        for (int i = 1; i <= 4; i++) {
            assertThat(metricasDiariasRepository.findByAtletaIdAndData(atleta.getId(), d0.plusDays(i)).orElseThrow().getTss())
                    .as("TSS de D0+%d", i).isZero();
        }
        assertThat(ctlD0).isGreaterThan(ctlD1);
        assertThat(ctlD1).isGreaterThan(ctlD2);
        assertThat(ctlD2).isGreaterThan(ctlD3);
        assertThat(ctlD3).isGreaterThan(ctlD4);
        assertThat(ctlD4).as("nunca reiniciou em zero").isGreaterThan(0.0);
        assertThat(ctlD5).as("o treino de D0+5 volta a subir o CTL").isGreaterThan(ctlD4);
    }

    @Test
    @DisplayName("CA7: o PMC do atleta tem um ponto por dia entre a primeira métrica e hoje")
    void pmcDensoDaPrimeiraMetricaAteHoje() {
        LocalDate hoje = LocalDate.now(SP);
        LocalDate d0 = hoje.minusDays(10);
        Atleta atleta = cenario(d0);

        TenantContext.setTenantId(atleta.getAssessoria().getId());
        List<PmcPontoDto> pmc = atletaProgressService.getHistoricoPmc(atleta.getId(), d0, hoje);

        assertThat(pmc).extracting(PmcPontoDto::data)
                .containsExactlyElementsOf(d0.datesUntil(hoje.plusDays(1)).toList());
        assertThat(pmc).extracting(PmcPontoDto::ctl).doesNotContainNull().allSatisfy(c -> assertThat(c).isGreaterThan(0.0));
    }

    /** Treino em D0 e em D0+5, ingerido só depois: D0+1..D0+4 não têm nenhuma linha antes do recálculo. */
    private Atleta cenario(LocalDate d0) {
        Atleta atleta = criarAtleta();
        salvarTreino(atleta, d0);
        tsbService.atualizarTsbDia(atleta.getId(), d0);
        salvarTreino(atleta, d0.plusDays(5));

        tsbService.recalcularDesde(atleta.getId(), d0.plusDays(5));
        return atleta;
    }

    private double ctl(Atleta atleta, LocalDate data) {
        MetricasDiarias m = metricasDiariasRepository.findByAtletaIdAndData(atleta.getId(), data).orElseThrow();
        return m.getCtl();
    }

    private Atleta criarAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria PMC denso");
        assessoria.setDominio("pmc-denso-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta PMC denso");
        atleta.setEmail("pmc-denso-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Provar a série densa");
        atleta.setNivelExperiencia(NivelExperiencia.AVANCADO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta.setCtlTimeConstant(42);
        atleta.setAtlTimeConstant(7);
        atleta = atletaRepository.save(atleta);

        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(assessoria);
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        planoMetadadosRepository.save(meta);
        return atleta;
    }

    private void salvarTreino(Atleta atleta, LocalDate data) {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setAtleta(atleta);
        treino.setDataTreino(data);
        treino.setDiaSemana(DiaSemana.values()[data.getDayOfWeek().getValue() % 7]);
        treino.setTipoTreino(TipoTreino.FACIL);
        treino.setDuracaoMin(Duration.ofMinutes(40));
        treino.setPercepcaoEsforco(8);
        treino.setDistanciaKm(new BigDecimal("8.00"));
        treino.setTssCalculado(tssCalculatorService.calcularTss(treino));
        treinoRealizadoRepository.save(treino);
    }
}
