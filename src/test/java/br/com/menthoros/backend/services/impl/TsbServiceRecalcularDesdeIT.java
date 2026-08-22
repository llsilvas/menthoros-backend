package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
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
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.TsbService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D13 — {@code TsbServiceImpl.atualizarTsbDia(D)} deriva CTL/ATL de {@code MetricasDiarias(D-1)}
 * (achado do pre-mortem, Codex #1): mudar o TSS de um dia passado invalida todos os dias
 * seguintes, não só o dia alterado. Antes de {@code recalcularDesde} existir, os caminhos de
 * ingestão retroativos recalculavam só o dia do treino — o bug que este teste prova corrigido.
 */
class TsbServiceRecalcularDesdeIT extends AbstractIntegrationTest {

    private static final int DIAS_HISTORICO = 6; // D-5 .. hoje

    @Autowired
    private TsbService tsbService;
    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private AtletaRepository atletaRepository;
    @Autowired
    private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired
    private TreinoRealizadoRepository treinoRealizadoRepository;
    @Autowired
    private MetricasDiariasRepository metricasDiariasRepository;

    @Test
    @DisplayName("recalcularDesde propaga o TSS de D-3 até hoje — CA6b")
    void recalcularDesdePropagaAteHoje() {
        LocalDate hoje = LocalDate.now();
        LocalDate d3 = hoje.minusDays(3);

        Atleta comRecalculoDesde = seedAtletaComHistoricoDeRepouso("RecalculoDesde");
        Atleta somenteDiaAlterado = seedAtletaComHistoricoDeRepouso("SomenteDiaAlterado");

        // Ambos ganham o mesmo treino em D-3, mudando o TSS daquele dia de 0 para > 0.
        salvarTreinoComEsforco(comRecalculoDesde, d3);
        salvarTreinoComEsforco(somenteDiaAlterado, d3);

        // Caminho antigo (o bug): só o dia alterado é recalculado.
        tsbService.atualizarTsbDia(somenteDiaAlterado.getId(), d3);

        // Caminho novo (D13): recalcula de D-3 até o último dia materializado.
        tsbService.recalcularDesde(comRecalculoDesde.getId(), d3);

        // No atleta com recalcularDesde, a carga se propaga por todos os dias seguintes.
        for (LocalDate data = d3; !data.isAfter(hoje); data = data.plusDays(1)) {
            MetricasDiarias metricas = buscar(comRecalculoDesde, data);
            assertThat(metricas.getCtl())
                    .as("CTL de %s deveria refletir o TSS de D-3 propagado", data)
                    .isNotEqualTo(0.0);
        }

        // No atleta só-dia-alterado, D-2/D-1/hoje continuam com a métrica antiga (zerada) —
        // é exatamente a inconsistência que recalcularDesde existe para eliminar.
        for (LocalDate data = d3.plusDays(1); !data.isAfter(hoje); data = data.plusDays(1)) {
            MetricasDiarias metricas = buscar(somenteDiaAlterado, data);
            assertThat(metricas.getCtl().doubleValue())
                    .as("sem recalcularDesde, %s fica stale (bug que esta mudança corrige)", data)
                    .isEqualTo(0.0);
        }
    }

    private MetricasDiarias buscar(Atleta atleta, LocalDate data) {
        return metricasDiariasRepository.findByAtletaIdAndData(atleta.getId(), data)
                .orElseThrow(() -> new AssertionError("Métrica não materializada para " + data));
    }

    private Atleta seedAtletaComHistoricoDeRepouso(String prefixo) {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria " + prefixo);
        assessoria.setDominio(prefixo.toLowerCase() + "-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta " + prefixo);
        atleta.setEmail(prefixo.toLowerCase() + "-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Provar recalcularDesde");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
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

        // Materializa D-5..hoje como dias de repouso (sem treino, TSS=0, CTL/ATL/TSB=0).
        LocalDate base = LocalDate.now().minusDays(DIAS_HISTORICO - 1L);
        for (int i = 0; i < DIAS_HISTORICO; i++) {
            tsbService.atualizarTsbDia(atleta.getId(), base.plusDays(i));
        }
        return atleta;
    }

    private void salvarTreinoComEsforco(Atleta atleta, LocalDate data) {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setAtleta(atleta);
        treino.setDataTreino(data);
        treino.setDiaSemana(diaSemanaDe(data));
        treino.setTipoTreino(TipoTreino.FACIL);
        treino.setDuracaoMin(Duration.ofMinutes(40));
        treino.setPercepcaoEsforco(8);
        treino.setDistanciaKm(new BigDecimal("8.00"));
        treinoRealizadoRepository.save(treino);
    }

    private DiaSemana diaSemanaDe(LocalDate data) {
        DayOfWeek dow = data.getDayOfWeek();
        return switch (dow) {
            case MONDAY -> DiaSemana.SEGUNDA;
            case TUESDAY -> DiaSemana.TERCA;
            case WEDNESDAY -> DiaSemana.QUARTA;
            case THURSDAY -> DiaSemana.QUINTA;
            case FRIDAY -> DiaSemana.SEXTA;
            case SATURDAY -> DiaSemana.SABADO;
            case SUNDAY -> DiaSemana.DOMINGO;
        };
    }
}
