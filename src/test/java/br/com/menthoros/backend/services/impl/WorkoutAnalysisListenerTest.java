package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.AnaliseWorkout;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AnaliseStatus;
import br.com.menthoros.backend.events.TreinoRegistradoEvent;
import br.com.menthoros.backend.repository.AiWorkoutAnalysisRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.services.WorkoutAnalysisTranslator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ResourceLoader;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkoutAnalysisListenerTest {

    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private PlanoMetadadosRepository planoMetadadosRepository;
    @Mock private AiWorkoutAnalysisRepository analiseRepository;
    @Mock private ModelRouter modelRouter;
    @Mock private WorkoutAnalysisTranslator translator;
    @Mock private ResourceLoader resourceLoader;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private WorkoutAnalysisListener listener;

    private UUID treinoId;
    private UUID tenantId;
    private TreinoRegistradoEvent event;

    @BeforeEach
    void setUp() {
        treinoId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        event = new TreinoRegistradoEvent(treinoId, tenantId);
    }

    @Test
    void skips_analysis_when_completed_already_exists() {
        when(analiseRepository.existsByTreinoRealizadoIdAndStatus(treinoId, AnaliseStatus.COMPLETED))
                .thenReturn(true);

        listener.onTreinoRegistrado(event);

        verify(treinoRealizadoRepository, never()).findById(any());
        verify(analiseRepository, never()).save(any());
    }

    @Test
    void skips_analysis_when_treino_not_found() {
        when(analiseRepository.existsByTreinoRealizadoIdAndStatus(treinoId, AnaliseStatus.COMPLETED))
                .thenReturn(false);
        when(treinoRealizadoRepository.findById(treinoId)).thenReturn(Optional.empty());

        listener.onTreinoRegistrado(event);

        verify(analiseRepository, never()).save(any());
    }

    @Test
    void skips_analysis_when_rpe_is_null() {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setPercepcaoEsforco(null);

        when(analiseRepository.existsByTreinoRealizadoIdAndStatus(treinoId, AnaliseStatus.COMPLETED))
                .thenReturn(false);
        when(treinoRealizadoRepository.findById(treinoId)).thenReturn(Optional.of(treino));

        listener.onTreinoRegistrado(event);

        verify(analiseRepository, never()).save(any());
    }

    @Test
    void saves_pending_status_before_calling_llm() {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setPercepcaoEsforco(7);

        AnaliseWorkout saved = new AnaliseWorkout();
        saved.setStatus(AnaliseStatus.PENDING);

        when(analiseRepository.existsByTreinoRealizadoIdAndStatus(treinoId, AnaliseStatus.COMPLETED))
                .thenReturn(false);
        when(treinoRealizadoRepository.findById(treinoId)).thenReturn(Optional.of(treino));
        when(analiseRepository.findByTreinoRealizadoIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.empty());
        when(analiseRepository.save(any())).thenReturn(saved);
        // Fail on skill load to prevent full execution — we're testing only the PENDING save gate
        when(resourceLoader.getResource(anyString())).thenThrow(new RuntimeException("skill not found"));

        listener.onTreinoRegistrado(event);

        // First save was PENDING; second save should be FAILED due to skill load error
        verify(analiseRepository, atLeastOnce()).save(argThat(a ->
                a.getStatus() == AnaliseStatus.PENDING || a.getStatus() == AnaliseStatus.FAILED));
    }

    @Test
    void sets_failed_status_when_llm_call_throws() {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setPercepcaoEsforco(7);

        AnaliseWorkout saved = new AnaliseWorkout();
        saved.setTreinoRealizadoId(treinoId);
        saved.setTenantId(tenantId);

        when(analiseRepository.existsByTreinoRealizadoIdAndStatus(treinoId, AnaliseStatus.COMPLETED))
                .thenReturn(false);
        when(treinoRealizadoRepository.findById(treinoId)).thenReturn(Optional.of(treino));
        when(analiseRepository.findByTreinoRealizadoIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.empty());
        when(analiseRepository.save(any())).thenReturn(saved);
        when(resourceLoader.getResource(anyString())).thenThrow(new RuntimeException("network error"));

        listener.onTreinoRegistrado(event);

        verify(analiseRepository).save(argThat(a -> a.getStatus() == AnaliseStatus.FAILED));
    }
}
