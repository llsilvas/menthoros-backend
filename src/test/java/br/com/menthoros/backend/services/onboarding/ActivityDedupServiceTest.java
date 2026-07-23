package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.entity.AtividadeProvenienciaDescartada;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.repository.AtividadeProvenienciaDescartadaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.onboarding.impl.ActivityDedupServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityDedupServiceTest {

    @Mock
    private TreinoRealizadoRepository treinoRealizadoRepository;

    @Mock
    private AtividadeProvenienciaDescartadaRepository provenienciaRepository;

    private ActivityDedupServiceImpl dedupService;

    private UUID tenantId;
    private UUID athleteId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        athleteId = UUID.randomUUID();
        // findAndRegisterModules() espelha o ObjectMapper gerenciado pelo Spring Boot em producao
        // (jackson-datatype-jsr310 no classpath, auto-configurado) — sem isso, serializar
        // NormalizedActivity.date (LocalDate) falha silenciosamente e mascararia o teste.
        dedupService = new ActivityDedupServiceImpl(treinoRealizadoRepository, provenienciaRepository, new ObjectMapper().findAndRegisterModules());
    }

    @Nested
    @DisplayName("deduplicar")
    class Deduplicar {

        @Test
        @DisplayName("mesma atividade em 2 fontes no mesmo dia -> merge, retem a de maior prioridade")
        void mesmaAtividadeDuasFontesMerge() {
            NormalizedActivity garmin = atividade("g1", FonteDados.GARMIN, LocalDate.of(2026, 7, 1), 45, 10.0);
            NormalizedActivity strava = atividade("s1", FonteDados.STRAVA, LocalDate.of(2026, 7, 1), 46, 10.1);

            TreinoRealizado treinoGarmin = new TreinoRealizado();
            treinoGarmin.setId(garmin.treinoRealizadoId());
            when(treinoRealizadoRepository.findById(garmin.treinoRealizadoId())).thenReturn(Optional.of(treinoGarmin));

            List<NormalizedActivity> resultado = dedupService.deduplicar(List.of(garmin, strava), tenantId);

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).source()).isEqualTo(FonteDados.GARMIN);
        }

        @Test
        @DisplayName("merge grava a fonte descartada na auditoria com FK pra atividade vencedora")
        void mergeGravaAuditoria() {
            NormalizedActivity garmin = atividade("g1", FonteDados.GARMIN, LocalDate.of(2026, 7, 1), 45, 10.0);
            NormalizedActivity strava = atividade("s1", FonteDados.STRAVA, LocalDate.of(2026, 7, 1), 46, 10.1);

            TreinoRealizado treinoGarmin = new TreinoRealizado();
            treinoGarmin.setId(garmin.treinoRealizadoId());
            when(treinoRealizadoRepository.findById(garmin.treinoRealizadoId())).thenReturn(Optional.of(treinoGarmin));

            dedupService.deduplicar(List.of(garmin, strava), tenantId);

            ArgumentCaptor<AtividadeProvenienciaDescartada> captor = ArgumentCaptor.forClass(AtividadeProvenienciaDescartada.class);
            verify(provenienciaRepository).save(captor.capture());
            AtividadeProvenienciaDescartada auditoria = captor.getValue();
            assertThat(auditoria.getFonteDescartada()).isEqualTo(FonteDados.STRAVA);
            assertThat(auditoria.getAtividade().getId()).isEqualTo(garmin.treinoRealizadoId());
            assertThat(auditoria.getTenantId()).isEqualTo(tenantId);
            assertThat(auditoria.getDadosDescartados())
                    .as("dados descartados devem conter o payload real, nao um fallback vazio")
                    .contains("\"s1\"", "\"STRAVA\"")
                    .isNotEqualTo("{}");
        }

        @Test
        @DisplayName("atividades distintas no mesmo dia (fora da similaridade) -> nao faz merge")
        void atividadesDistintasMesmoDiaNaoMerge() {
            NormalizedActivity manha = atividade("g1", FonteDados.GARMIN, LocalDate.of(2026, 7, 1), 30, 5.0);
            NormalizedActivity tarde = atividade("g2", FonteDados.GARMIN, LocalDate.of(2026, 7, 1), 90, 18.0);

            List<NormalizedActivity> resultado = dedupService.deduplicar(List.of(manha, tarde), tenantId);

            assertThat(resultado).hasSize(2);
            verify(provenienciaRepository, never()).save(any());
        }

        @Test
        @DisplayName("atividades em dias diferentes nunca fazem merge, mesmo se parecidas")
        void atividadesDiasDiferentesNaoMerge() {
            NormalizedActivity dia1 = atividade("g1", FonteDados.GARMIN, LocalDate.of(2026, 7, 1), 45, 10.0);
            NormalizedActivity dia2 = atividade("g2", FonteDados.GARMIN, LocalDate.of(2026, 7, 2), 45, 10.0);

            List<NormalizedActivity> resultado = dedupService.deduplicar(List.of(dia1, dia2), tenantId);

            assertThat(resultado).hasSize(2);
        }

        @Test
        @DisplayName("historico vazio devolve lista vazia, sem tocar repositorios")
        void historicoVazio() {
            List<NormalizedActivity> resultado = dedupService.deduplicar(List.of(), tenantId);

            assertThat(resultado).isEmpty();
            verify(provenienciaRepository, never()).save(any());
        }
    }

    private NormalizedActivity atividade(String activityId, FonteDados fonte, LocalDate data, int duracaoMin, double distanciaKm) {
        return new NormalizedActivity(
                UUID.randomUUID(),
                activityId,
                athleteId,
                data,
                Sport.RUNNING,
                duracaoMin,
                distanciaKm,
                150,
                170,
                Duration.ofSeconds(270),
                null,
                null,
                fonte,
                0.8
        );
    }
}
