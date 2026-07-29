package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
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
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.PlanoMetadadosService;
import br.com.menthoros.backend.services.TsbService;
import br.com.menthoros.backend.services.helper.TsbRecalculoExecutor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Observabilidade do recalculo (Decisoes 8 e CA9): invalidacao do cache de metadados e contador
 * de recalculos abortados.
 */
class TsbRecalculoObservabilidadeIT extends AbstractIntegrationTest {

    @Autowired
    private TsbService tsbService;
    @Autowired
    private PlanoMetadadosService planoMetadadosService;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private AtletaRepository atletaRepository;
    @Autowired
    private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired
    private TreinoRealizadoRepository treinoRealizadoRepository;

    @MockitoSpyBean
    private TsbRecalculoExecutor tsbRecalculoExecutor;

    private Atleta atleta;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        atleta = seedAtleta();
        tenantId = atleta.getAssessoria().getId();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("cache metadados-atleta")
    class CacheMetadados {

        @Test
        @DisplayName("recalculo invalida a entrada de cache do atleta (CA9)")
        void recalculoInvalidaCache() {
            salvarTreino(atleta, LocalDate.now().minusDays(3));

            // Popula o cache pelo mesmo caminho que a geracao de plano usa.
            planoMetadadosService.buscarOuCriarMetadados(atleta);
            String chave = atleta.getId() + "_" + tenantId;

            Cache cache = cacheManager.getCache("metadados-atleta");
            assertThat(cache).isNotNull();
            assertThat(cache.get(chave))
                    .as("pre-condicao: a entrada tem de estar cacheada")
                    .isNotNull();

            tsbService.recalcularHistoricoCompleto(atleta.getId());

            assertThat(cache.get(chave))
                    .as("sem invalidacao, a geracao de plano leria CTL/TSB anteriores ao recalculo")
                    .isNull();
        }

        @Test
        @DisplayName("atleta sem historico tambem invalida o cache")
        void semHistoricoInvalidaCache() {
            planoMetadadosService.buscarOuCriarMetadados(atleta);
            String chave = atleta.getId() + "_" + tenantId;
            Cache cache = cacheManager.getCache("metadados-atleta");
            assertThat(cache.get(chave)).isNotNull();

            tsbService.recalcularHistoricoCompleto(atleta.getId());

            assertThat(cache.get(chave)).isNull();
        }
    }

    @Nested
    @DisplayName("contador tsb.recalculo.abortado")
    class ContadorAbortos {

        @Test
        @DisplayName("falha em bloco incrementa o contador com tag fase=blocos")
        void falhaEmBlocoIncrementa() {
            salvarTreino(atleta, LocalDate.now().minusDays(3));
            double antes = contador("blocos");

            doThrow(new IllegalStateException("falha injetada no bloco"))
                    .when(tsbRecalculoExecutor).recalcularBloco(any(), any(), any(), any());

            assertThatThrownBy(() -> tsbService.recalcularHistoricoCompleto(atleta.getId()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("falhou no bloco");

            assertThat(contador("blocos"))
                    .as("um recalculo abortado tem de deixar rastro — a metrica de sucesso depende disso")
                    .isEqualTo(antes + 1);
        }

        private double contador(String fase) {
            return Search.in(meterRegistry)
                    .name("tsb.recalculo.abortado")
                    .tag("fase", fase)
                    .counters().stream()
                    .mapToDouble(io.micrometer.core.instrument.Counter::count)
                    .sum();
        }
    }

    // ---- fixtures ----

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Observabilidade");
        assessoria.setDominio("obs-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta a = new Atleta();
        a.setNome("Atleta Observabilidade");
        a.setEmail("obs-" + UUID.randomUUID() + "@test.com");
        a.setObjetivo("Validar cache e metrica do recalculo");
        a.setNivelExperiencia(NivelExperiencia.AVANCADO);
        a.setAtivo(AtletaStatus.ATIVO);
        a.setAssessoria(assessoria);
        a.setCtlTimeConstant(42);
        a.setAtlTimeConstant(7);
        a = atletaRepository.save(a);

        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(a);
        meta.setAssessoria(assessoria);
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        planoMetadadosRepository.save(meta);

        return a;
    }

    private void salvarTreino(Atleta a, LocalDate data) {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setAtleta(a);
        treino.setDataTreino(data);
        treino.setDiaSemana(diaSemanaDe(data));
        treino.setTipoTreino(TipoTreino.FACIL);
        treino.setDuracaoMin(Duration.ofMinutes(36));
        treino.setPercepcaoEsforco(8);
        treino.setDistanciaKm(new BigDecimal("10.00"));
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
