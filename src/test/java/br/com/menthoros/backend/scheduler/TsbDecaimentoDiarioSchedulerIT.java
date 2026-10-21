package br.com.menthoros.backend.scheduler;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.projection.SerieMetricasAtleta;
import br.com.menthoros.backend.services.MelhorEsforcoService;
import br.com.menthoros.backend.services.TsbService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * fix-tsb-rest-days-materialization, R3/R5/R7 com o job real contra Postgres. O job varre todos os
 * atletas do banco; as asserções olham só os atletas de cada teste.
 */
class TsbDecaimentoDiarioSchedulerIT extends AbstractIntegrationTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired private TsbDecaimentoDiarioScheduler scheduler;
    @Autowired private TsbService tsbService;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired private MetricasDiariasRepository metricasDiariasRepository;

    @MockitoSpyBean private MelhorEsforcoService melhorEsforcoService;

    @Test
    @DisplayName("atraso de 900 dias converge em 3 execuções, sem reiniciar o CTL em zero (R7)")
    void atrasoDe900DiasConvergeEmTresExecucoes() {
        LocalDate hoje = LocalDate.now(SP);
        LocalDate inicio = hoje.minusDays(900);
        Atleta atleta = criarAtleta("Atraso900");
        tsbService.atualizarTsbDia(atleta.getId(), inicio);

        scheduler.executar();
        assertThat(serieDe(atleta).getUltimaData()).isEqualTo(inicio.plusDays(400));

        scheduler.executar();
        assertThat(serieDe(atleta).getUltimaData()).isEqualTo(inicio.plusDays(800));

        scheduler.executar();
        SerieMetricasAtleta fim = serieDe(atleta);
        assertThat(fim.getUltimaData()).isEqualTo(hoje);
        assertThat(fim.primeiroDiaPendente(hoje)).isEmpty();
        assertThat(fim.getPrimeiroBuraco()).isNull();
        assertThat(metricasDiariasRepository.findByAtletaIdAndData(atleta.getId(), hoje))
                .get().extracting(m -> m.getCtl()).isNotNull();
    }

    @Test
    @DisplayName("dois tenants: a busca de melhor esforço do job roda com o tenant do próprio atleta (R5)")
    void melhorEsforcoRodaComOTenantDoAtleta() {
        LocalDate hoje = LocalDate.now(SP);
        Atleta a = criarAtleta("TenantA");
        Atleta b = criarAtleta("TenantB");
        for (Atleta atleta : new Atleta[]{a, b}) {
            tsbService.atualizarTsbDia(atleta.getId(), hoje.minusDays(3));
        }
        // Só a partir daqui: as chamadas do setup acima rodam sem tenant e não dizem nada sobre o job.
        Map<UUID, UUID> tenantNaChamada = new ConcurrentHashMap<>();
        doAnswer(inv -> {
            tenantNaChamada.put(inv.getArgument(0), TenantContext.getTenantId());
            return List.of();
        }).when(melhorEsforcoService).buscar(any(), any());

        scheduler.executar();

        for (Atleta atleta : new Atleta[]{a, b}) {
            assertThat(serieDe(atleta).getUltimaData()).isEqualTo(hoje);
            assertThat(tenantNaChamada.get(atleta.getId()))
                    .as("tenant visto pela busca de melhor esforço do atleta %s", atleta.getNome())
                    .isEqualTo(atleta.getAssessoria().getId());
        }
        assertThat(TenantContext.getTenantId()).as("job não deixa tenant na thread").isNull();
    }

    private SerieMetricasAtleta serieDe(Atleta atleta) {
        return metricasDiariasRepository.findSeriePorAtleta().stream()
                .filter(x -> x.getAtletaId().equals(atleta.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("atleta sem série: " + atleta.getId()));
    }

    private Atleta criarAtleta(String prefixo) {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria " + prefixo);
        assessoria.setDominio(prefixo.toLowerCase() + "-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta " + prefixo);
        atleta.setEmail(prefixo.toLowerCase() + "-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Provar o job de decaimento");
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
        return atleta;
    }
}
