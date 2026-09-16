package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.services.IngestaoTreinoRealizadoService;
import br.com.menthoros.backend.services.helper.TreinoDedupHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT de {@code fix-progressao-continua-incremental} (CA1) — prova que
 * {@code PlanoMetaDados.semanasProgressaoContinua} fica em dia pelo caminho real de ingestão
 * ({@link IngestaoTreinoRealizadoService#registrar}), sem depender de
 * {@code TsbService.recalcularHistoricoCompleto}.
 */
class TsbServiceProgressaoContinuaIT extends AbstractIntegrationTest {

    @Autowired
    private IngestaoTreinoRealizadoService ingestaoTreinoRealizadoService;
    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private AtletaRepository atletaRepository;
    @Autowired
    private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired
    private MetricasDiariasRepository metricasDiariasRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("registrar — mantém semanasProgressaoContinua em dia pelo caminho incremental [CA1]")
    class MantemStreakEmDia {

        @Test
        @DisplayName("streak reflete o histórico real sem recalcularHistoricoCompleto")
        void streakRefleteHistoricoReal() {
            Atleta atleta = seedAtleta("Progressao");

            // 3 semanas de volume crescente (10, 20, 30 km), cada uma numa semana ISO distinta —
            // qualquer dia dentro da semana serve, o agrupamento em recalcularSemanasProgressao
            // ancora pela segunda-feira da semana da data.
            seedMetricaSemana(atleta, LocalDate.now().minusDays(21), BigDecimal.valueOf(10));
            seedMetricaSemana(atleta, LocalDate.now().minusDays(14), BigDecimal.valueOf(20));
            seedMetricaSemana(atleta, LocalDate.now().minusDays(7), BigDecimal.valueOf(30));

            // 4ª semana (hoje): treino real registrado pelo caminho de ingestão, com volume maior
            // que a semana anterior — estende o streak para a 4ª semana consecutiva de progressão.
            LocalDate hoje = LocalDate.now();
            TreinoRealizado treino = novoRealizado(atleta, hoje, BigDecimal.valueOf(40));
            ingestaoTreinoRealizadoService.registrar(treino, null);

            PlanoMetaDados metaDados = planoMetadadosRepository.findByAtletaId(atleta.getId()).orElseThrow();
            assertThat(metaDados.getSemanasProgressaoContinua()).isEqualTo(3);
        }

        @Test
        @DisplayName("registrar de novo o mesmo treino (idempotência) não muda o streak")
        void idempotenteNaRepeticao() {
            Atleta atleta = seedAtleta("ProgressaoIdempotente");
            seedMetricaSemana(atleta, LocalDate.now().minusDays(14), BigDecimal.valueOf(10));
            seedMetricaSemana(atleta, LocalDate.now().minusDays(7), BigDecimal.valueOf(20));

            LocalDate hoje = LocalDate.now();
            String externalId = "ext-" + UUID.randomUUID();
            TreinoRealizado primeiro = novoRealizado(atleta, hoje, BigDecimal.valueOf(30));
            ingestaoTreinoRealizadoService.registrar(primeiro, externalId);

            int streakAposPrimeiroRegistro = planoMetadadosRepository
                    .findByAtletaId(atleta.getId()).orElseThrow().getSemanasProgressaoContinua();

            TreinoRealizado segundo = novoRealizado(atleta, hoje, BigDecimal.valueOf(30));
            ingestaoTreinoRealizadoService.registrar(segundo, externalId);

            int streakAposSegundoRegistro = planoMetadadosRepository
                    .findByAtletaId(atleta.getId()).orElseThrow().getSemanasProgressaoContinua();

            assertThat(streakAposSegundoRegistro).isEqualTo(streakAposPrimeiroRegistro);
        }
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
        atleta.setObjetivo("Provar o streak incremental");
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

    private void seedMetricaSemana(Atleta atleta, LocalDate data, BigDecimal volumeKm) {
        MetricasDiarias metrica = MetricasDiarias.builder()
                .atleta(atleta)
                .tenantId(atleta.getAssessoria().getId())
                .data(data)
                .volumeKm(volumeKm)
                .treinosRealizados(1)
                .tss(0)
                .ctl(0.0)
                .atl(0.0)
                .tsb(0.0)
                .build();
        metricasDiariasRepository.save(metrica);
    }

    private TreinoRealizado novoRealizado(Atleta atleta, LocalDate data, BigDecimal distanciaKm) {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setAtleta(atleta);
        tr.setDataTreino(data);
        tr.setDiaSemana(DiaSemana.SABADO);
        tr.setTipoTreino(TipoTreino.FACIL);
        tr.setDuracaoMin(Duration.ofMinutes(40));
        tr.setDistanciaKm(distanciaKm);
        tr.setFonteDados(FonteDados.MANUAL);
        tr.setPercepcaoEsforco(7);
        return tr;
    }
}
