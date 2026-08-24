package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT de {@code gravarEtapas} — task 7.5 (`ingestao-treino-realizado`): o backfill de laps do
 * intervals.icu passa a chamar {@code reprocessar} depois de gravar as etapas, fechando CA5
 * (etapas mudam {@code tssCalculado}/carga do dia) no caminho real.
 */
class IntervalsIcuLapsBackfillPersisterIT extends AbstractIntegrationTest {

    @Autowired
    private IntervalsIcuLapsBackfillPersister persister;
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
    @DisplayName("etapas gravadas mudam tssCalculado e a carga do dia [CA5]")
    void etapasGravadasMudamTssECarga() {
        Atleta atleta = seedAtleta("LapsBackfill");
        LocalDate data = LocalDate.now().minusDays(1);
        TreinoRealizado treino = novoRealizado(atleta, data);
        // Sem duração/distância/FC — TssCalculatorService estimaria pouco/nada até a etapa chegar.
        treino.setDuracaoMin(null);
        treino.setDistanciaKm(null);
        treino = treinoRealizadoRepository.save(treino);
        Integer tssAntes = treino.getTssCalculado();

        EtapaRealizada etapa = new EtapaRealizada();
        etapa.setOrdem(1);
        etapa.setDistanciaKm(new BigDecimal("10.00"));
        etapa.setDuracao(Duration.ofMinutes(50));
        etapa.setFcMedia(160);

        persister.gravarEtapas(treino.getId(), List.of(etapa), atleta.getAssessoria().getId());

        TreinoRealizado apos = treinoRealizadoRepository.findById(treino.getId()).orElseThrow();
        assertThat(apos.getTssCalculado()).as("tssCalculado passa a refletir a etapa gravada").isNotEqualTo(tssAntes);

        MetricasDiarias metricas = metricasDiariasRepository.findByAtletaIdAndData(atleta.getId(), data).orElseThrow();
        assertThat(metricas.getCtl()).as("carga do dia recalculada após gravar etapas").isNotEqualTo(0.0);
    }

    @Test
    @DisplayName("sem etapas (lista vazia) não chama reprocessar — treino permanece intocado")
    void semEtapasNaoAlteraTreino() {
        Atleta atleta = seedAtleta("LapsBackfillVazio");
        LocalDate data = LocalDate.now().minusDays(1);
        TreinoRealizado treino = treinoRealizadoRepository.save(novoRealizado(atleta, data));

        persister.gravarEtapas(treino.getId(), List.of(), atleta.getAssessoria().getId());

        assertThat(metricasDiariasRepository.findByAtletaIdAndData(atleta.getId(), data)).isEmpty();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private Atleta seedAtleta(String prefixo) {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria " + prefixo);
        assessoria.setDominio(prefixo.toLowerCase() + "-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta " + prefixo);
        atleta.setEmail(prefixo.toLowerCase() + "-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Provar o backfill de laps");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);

        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(assessoria);
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        planoMetadadosRepository.save(meta);

        return atleta;
    }

    private TreinoRealizado novoRealizado(Atleta atleta, LocalDate data) {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setAtleta(atleta);
        tr.setTenantId(atleta.getAssessoria().getId());
        tr.setDataTreino(data);
        tr.setDiaSemana(DiaSemana.SABADO);
        tr.setTipoTreino(TipoTreino.FACIL);
        tr.setFonteDados(FonteDados.INTERVALS_ICU);
        return tr;
    }
}
