package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.domain.workout.StructuredWorkout;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.services.PushResult;
import br.com.menthoros.backend.services.WorkoutChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkoutChannelPorPlataformaTest {

    private final IntervalsIcuAdapter intervals = mock(IntervalsIcuAdapter.class);
    private final GarminTrainingAdapter garmin = mock(GarminTrainingAdapter.class);
    private WorkoutChannelPorPlataforma canal;

    private final StructuredWorkout workout = new StructuredWorkout("menthoros-1", "Longao", null,
            LocalDate.of(2026, 11, 4), null, List.of());
    private final LocalDate inicio = LocalDate.of(2026, 11, 2);
    private final LocalDate fim = LocalDate.of(2026, 11, 8);

    @BeforeEach
    void setUp() {
        canal = new WorkoutChannelPorPlataforma(intervals, garmin);
    }

    private static IntegracaoExterna conexao(FonteDados plataforma) {
        IntegracaoExterna c = new IntegracaoExterna();
        c.setPlataforma(plataforma);
        return c;
    }

    @Test
    @DisplayName("GARMIN: as tres operacoes vao so para o adapter Garmin")
    void delegaParaGarmin() {
        IntegracaoExterna c = conexao(FonteDados.GARMIN);
        when(garmin.push(c, workout, 9L)).thenReturn(PushResult.okAtualizado(9L));

        assertThat(canal.push(c, workout, 9L).eventId()).isEqualTo(9L);
        canal.removerOrfaos(c, inicio, fim, Set.of("a"), Set.of("9"));
        canal.tocarEvento(c, 9L, "menthoros-1");

        verify(garmin).removerOrfaos(c, inicio, fim, Set.of("a"), Set.of("9"));
        verify(garmin).tocarEvento(c, 9L, "menthoros-1");
        verifyNoInteractions(intervals);
    }

    @Test
    @DisplayName("INTERVALS_ICU: as tres operacoes vao so para o adapter do intervals.icu")
    void delegaParaIntervals() {
        IntegracaoExterna c = conexao(FonteDados.INTERVALS_ICU);
        when(intervals.push(c, workout, null)).thenReturn(PushResult.okCriado(5L));

        assertThat(canal.push(c, workout, null).eventId()).isEqualTo(5L);
        canal.removerOrfaos(c, inicio, fim, Set.of("a"), Set.of());
        canal.tocarEvento(c, 5L, "menthoros-1");

        verify(intervals).removerOrfaos(c, inicio, fim, Set.of("a"), Set.of());
        verify(intervals).tocarEvento(c, 5L, "menthoros-1");
        verifyNoInteractions(garmin);
    }

    @Test
    @DisplayName("plataforma sem canal de treinos e erro de programacao, nao push silencioso")
    void plataformaSemCanal() {
        assertThatThrownBy(() -> canal.push(conexao(FonteDados.STRAVA), workout, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("com dois adapters no contexto, WorkoutChannel resolve para o composto")
    void contextoSobeComDoisAdapters() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(IntervalsIcuAdapter.class, () -> intervals);
            ctx.registerBean(GarminTrainingAdapter.class, () -> garmin);
            ctx.register(WorkoutChannelPorPlataforma.class);
            ctx.refresh();

            assertThat(ctx.getBean(WorkoutChannel.class)).isInstanceOf(WorkoutChannelPorPlataforma.class);
        }
    }
}
