package br.com.menthoros.backend.services;

import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.EtapaTreino;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Monta o JSON de dados do treino enviado ao LLM nas duas chamadas da análise pós-treino
 * (análise do coach e bloco do atleta — mesma base, change {@code analise-ia-treino-atleta}).
 *
 * <p><b>Só campos numéricos e enums — nunca texto livre</b> ({@code feedbackAtleta},
 * {@code observacao}, {@code descricaoEtapa}): conteúdo digitado pelo usuário no prompt é vetor
 * de injeção. Os alvos declarados pelo coach ({@code fcAlvoEtapa}, {@code ritmoAlvo}) entram por
 * serem alvos estruturados curtos que a análise compara — a mesma base que o push ao relógio lê.
 *
 * <p>Duração, pace e etapas existem no payload para que o texto cite fatos da sessão em vez de
 * inventá-los (pré-mortem Codex #3): a skill instrui "cite só números presentes nos dados".
 */
@Component
@RequiredArgsConstructor
public class WorkoutAnalysisPromptDataBuilder {

    private final PlanoMetadadosRepository planoMetadadosRepository;
    private final ObjectMapper objectMapper;

    /**
     * Idempotent: YES — mesma entrada, mesmo JSON.
     * Side Effects: Database read (PlanoMetaDados)
     * Tenant-aware: N/A — opera sobre a entidade já carregada pelo chamador.
     */
    public String build(TreinoRealizado treino) {
        Map<String, Object> data = new HashMap<>();

        Map<String, Object> planned = new HashMap<>();
        TreinoPlanejado planejado = treino.getTreinoPlanejado();
        if (planejado != null) {
            planned.put("type", planejado.getTipoTreino());
            planned.put("distance_km", planejado.getDistanciaKm());
            planned.put("expected_rpe", planejado.getPercepcaoEsforcoEsperada());
            putMinutos(planned, "duration_min", planejado.getDuracaoMin());
            List<EtapaTreino> etapas = planejado.getEtapas();
            if (etapas != null && !etapas.isEmpty()) {
                planned.put("steps", etapas.stream().map(this::plannedStep).toList());
            }
        }

        Map<String, Object> actual = new HashMap<>();
        actual.put("distance_km", treino.getDistanciaKm());
        actual.put("rpe", treino.getPercepcaoEsforco());
        if (treino.getFcMedia() != null) actual.put("avg_hr", treino.getFcMedia());
        putMinutos(actual, "duration_min", treino.getDuracaoMin());
        Double pace = avgPaceMinKm(treino);
        if (pace != null) actual.put("avg_pace_min_km", pace);
        List<EtapaRealizada> realizadas = treino.getEtapasRealizadas();
        if (realizadas != null && !realizadas.isEmpty()) {
            actual.put("steps", realizadas.stream().map(this::actualStep).toList());
        }

        Map<String, Object> context = new HashMap<>();
        Optional<PlanoMetaDados> metaDados = treino.getAtleta() != null
                ? planoMetadadosRepository.findByAtletaId(treino.getAtleta().getId())
                : Optional.empty();
        metaDados.ifPresent(m -> {
            context.put("tsb", m.getTsbAtual());
            context.put("ctl", m.getCtlAtual());
        });

        data.put("planned", planned);
        data.put("actual", actual);
        data.put("athlete_context", context);

        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao serializar dados do treino", e);
        }
    }

    private Map<String, Object> plannedStep(EtapaTreino e) {
        Map<String, Object> step = new HashMap<>();
        step.put("order", e.getOrdem());
        step.put("type", e.getTipoEtapa());
        step.put("duration_min", e.getDuracaoMin());
        step.put("distance_km", e.getDistanciaKm());
        step.put("hr_target", e.getFcAlvoEtapa());
        step.put("pace_target", e.getRitmoAlvo());
        step.put("repetitions", e.getRepeticoes());
        return step;
    }

    private Map<String, Object> actualStep(EtapaRealizada e) {
        Map<String, Object> step = new HashMap<>();
        step.put("order", e.getOrdem());
        step.put("type", e.getTipoEtapa());
        putMinutos(step, "duration_min", e.getDuracao());
        step.put("distance_km", e.getDistanciaKm());
        step.put("avg_hr", e.getFcMedia());
        step.put("max_hr", e.getFcMax());
        step.put("rpe", e.getPercepcaoEsforco());
        Duration paceEtapa = e.getPaceMedia();
        if (paceEtapa != null) step.put("avg_pace_min_km", minutosDecimais(paceEtapa));
        return step;
    }

    private static void putMinutos(Map<String, Object> map, String key, Duration duration) {
        if (duration != null) {
            map.put(key, duration.toMinutes());
        }
    }

    /** Pace em minutos decimais por km: `paceMedia` quando existe, senão duração/distância. */
    private static Double avgPaceMinKm(TreinoRealizado treino) {
        if (treino.getPaceMedia() != null) {
            return minutosDecimais(treino.getPaceMedia());
        }
        Duration duracao = treino.getDuracaoMin();
        BigDecimal distancia = treino.getDistanciaKm();
        if (duracao == null || distancia == null || distancia.signum() <= 0) {
            return null;
        }
        return BigDecimal.valueOf(duracao.toSeconds() / 60.0)
                .divide(distancia, 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static double minutosDecimais(Duration pace) {
        return BigDecimal.valueOf(pace.toSeconds() / 60.0)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
