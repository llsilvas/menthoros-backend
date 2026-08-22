package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.domain.workout.HrTarget;
import br.com.menthoros.backend.domain.workout.IntensityTarget;
import br.com.menthoros.backend.domain.workout.PaceTarget;
import br.com.menthoros.backend.domain.workout.StructuredWorkout;
import br.com.menthoros.backend.domain.workout.WorkoutStep;
import br.com.menthoros.backend.dto.intervalsicu.IcuEventDto;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.exception.IntervalsIcuApiException;
import br.com.menthoros.backend.services.IntervalsIcuClient;
import br.com.menthoros.backend.services.PushResult;
import br.com.menthoros.backend.services.WorkoutChannel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Adapter concreto do canal intervals.icu. Monta o {@code workout_doc} a partir do modelo
 * canônico {@link StructuredWorkout} e aplica o fluxo de idempotência (upsert por
 * {@code external_id}, já que a API não deduplica). Nunca lança — erros de rede/API viram
 * {@link PushResult#erro}, com mensagem curada que nunca inclui a API key.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntervalsIcuAdapter implements WorkoutChannel {

    private final IntervalsIcuClient client;
    private final ObjectMapper objectMapper;

    /**
     * Idempotent: NO — cria ou atualiza um evento no intervals.icu conforme o estado informado.
     * Side Effects: External API call (POST/PUT/GET events).
     * Tenant-aware: NO — credencial é do atleta (conexao), não do tenant.
     */
    @Override
    public PushResult push(IntegracaoExterna conexao, StructuredWorkout workout, Long eventIdArmazenado) {
        if (conexao == null) {
            throw new IllegalArgumentException("conexao não pode ser nula");
        }
        if (workout == null) {
            throw new IllegalArgumentException("workout não pode ser nulo");
        }
        String token = conexao.getAccessToken();
        String externalAthleteId = conexao.getExternalAthleteId();
        JsonNode payload = montarPayload(workout);

        try {
            if (eventIdArmazenado != null) {
                return atualizarOuRecriar(token, externalAthleteId, eventIdArmazenado, payload);
            }
            Long idExistente = buscarIdPorExternalId(token, externalAthleteId, workout);
            if (idExistente != null) {
                return atualizarOuRecriar(token, externalAthleteId, idExistente, payload);
            }
            IcuEventDto criado = client.criarEvento(token, externalAthleteId, payload);
            return PushResult.okCriado(criado.id());
        } catch (IntervalsIcuApiException e) {
            return PushResult.erro(mapearStatus(e), mensagemCurada(e));
        } catch (Exception e) {
            log.warn("Falha inesperada ao sincronizar treino com intervals.icu: {}", e.getMessage());
            return PushResult.erro(StatusSincronizacao.ERRO_TEMPORARIO,
                    "Erro inesperado ao sincronizar treino com o intervals.icu");
        }
    }

    /**
     * Idempotent: YES — deletar duas vezes é seguro (evento já ausente).
     * Side Effects: External API call (GET events + DELETE events/{id} por órfão encontrado).
     * Tenant-aware: NO — credencial é do atleta (conexao), não do tenant.
     */
    @Override
    public void removerOrfaos(IntegracaoExterna conexao, LocalDate inicio, LocalDate fim,
                               Set<String> externalIdsAtuais) {
        if (conexao == null) {
            throw new IllegalArgumentException("conexao não pode ser nula");
        }
        String token = conexao.getAccessToken();
        String externalAthleteId = conexao.getExternalAthleteId();
        Set<String> atuais = externalIdsAtuais == null ? Set.of() : externalIdsAtuais;

        List<IcuEventDto> eventos = client.listarEventos(token, externalAthleteId, inicio, fim);
        for (IcuEventDto evento : eventos) {
            String externalId = evento.externalId();
            if (externalId == null || !externalId.startsWith(StructuredWorkout.PREFIXO_EXTERNAL_ID)) {
                continue;
            }
            if (atuais.contains(externalId)) {
                continue;
            }
            try {
                client.deletarEvento(token, externalAthleteId, evento.id());
            } catch (IntervalsIcuApiException e) {
                if (!isStatus(e, 404)) {
                    log.warn("Falha ao remover evento órfão {} do intervals.icu: {}", evento.id(), e.getMessage());
                }
            }
        }
    }

    /**
     * Idempotent: YES — reenvia sempre o mesmo external_id (o valor não muda entre chamadas).
     * Side Effects: External API call (PUT events/{id} com payload mínimo).
     * Tenant-aware: NO — credencial é do atleta (conexao), não do tenant.
     *
     * <p>Best-effort por contrato: QUALQUER exceção (inclusive 404 — evento pode ter sido apagado
     * entre o push e o nudge) é absorvida e apenas logada — nunca propaga, para não colocar em
     * risco o estado do treino que já foi marcado como sincronizado.
     */
    @Override
    public void tocarEvento(IntegracaoExterna conexao, long eventId, String externalIdCanonico) {
        try {
            String token = conexao.getAccessToken();
            String externalAthleteId = conexao.getExternalAthleteId();
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("external_id", externalIdCanonico);
            client.atualizarEvento(token, externalAthleteId, eventId, payload);
        } catch (Exception e) {
            log.warn("Nudge anti-debounce falhou para o evento {} (best-effort, treino não é afetado): {}",
                    eventId, e.getMessage());
        }
    }

    // ===== Fluxo de idempotência =====

    private PushResult atualizarOuRecriar(String token, String externalAthleteId, long eventId, JsonNode payload) {
        try {
            IcuEventDto atualizado = client.atualizarEvento(token, externalAthleteId, eventId, payload);
            return PushResult.okAtualizado(atualizado.id());
        } catch (IntervalsIcuApiException e) {
            if (isStatus(e, 404)) {
                IcuEventDto criado = client.criarEvento(token, externalAthleteId, payload);
                return PushResult.okCriado(criado.id());
            }
            throw e;
        }
    }

    private Long buscarIdPorExternalId(String token, String externalAthleteId, StructuredWorkout workout) {
        LocalDate data = workout.scheduledDate();
        List<IcuEventDto> existentes = client.listarEventos(token, externalAthleteId, data, data);
        Optional<IcuEventDto> match = existentes.stream()
                .filter(ev -> workout.externalId().equals(ev.externalId()))
                .findFirst();
        return match.map(IcuEventDto::id).orElse(null);
    }

    private boolean isStatus(IntervalsIcuApiException e, int codigo) {
        HttpStatusCode status = e.getStatus();
        return status != null && status.value() == codigo;
    }

    // ===== Mapeamento de erro =====

    private StatusSincronizacao mapearStatus(IntervalsIcuApiException e) {
        HttpStatusCode status = e.getStatus();
        if (status == null) {
            return StatusSincronizacao.ERRO_TEMPORARIO;
        }
        int codigo = status.value();
        if (codigo == 401 || codigo == 403) {
            return StatusSincronizacao.ERRO_AUTENTICACAO;
        }
        if (codigo == 422) {
            return StatusSincronizacao.ERRO_VALIDACAO;
        }
        if (codigo == 429) {
            return StatusSincronizacao.ERRO_LIMITE_RATE;
        }
        return StatusSincronizacao.ERRO_TEMPORARIO;
    }

    private String mensagemCurada(IntervalsIcuApiException e) {
        return switch (mapearStatus(e)) {
            case ERRO_AUTENTICACAO -> "Falha de autenticação com o intervals.icu — verifique a API key";
            case ERRO_VALIDACAO -> "O intervals.icu rejeitou os dados do treino (validação)";
            case ERRO_LIMITE_RATE -> "Limite de requisições do intervals.icu atingido — nova tentativa em breve";
            default -> "Erro temporário ao comunicar com o intervals.icu — nova tentativa em breve";
        };
    }

    // ===== Montagem do payload =====

    private JsonNode montarPayload(StructuredWorkout workout) {
        ObjectNode evento = objectMapper.createObjectNode();
        evento.put("category", "WORKOUT");
        evento.put("start_date_local", workout.scheduledDate() + "T00:00:00");
        evento.put("type", "Run");
        evento.put("name", nomeComPrefixo(workout));
        evento.put("external_id", workout.externalId());

        ObjectNode workoutDoc = objectMapper.createObjectNode();
        if (workout.description() != null) {
            workoutDoc.put("description", workout.description());
        }
        ArrayNode steps = objectMapper.createArrayNode();
        for (WorkoutStep step : workout.steps()) {
            steps.add(montarStep(step));
        }
        workoutDoc.set("steps", steps);

        evento.set("workout_doc", workoutDoc);
        return evento;
    }

    private String nomeComPrefixo(StructuredWorkout workout) {
        return workout.namePrefix() != null
                ? workout.namePrefix() + " " + workout.name()
                : workout.name();
    }

    private ObjectNode montarStep(WorkoutStep step) {
        ObjectNode node = objectMapper.createObjectNode();
        if (step.text() != null) {
            node.put("text", step.text());
        }
        if (step.reps() != null) {
            node.put("reps", step.reps());
            ArrayNode subSteps = objectMapper.createArrayNode();
            for (WorkoutStep sub : step.steps()) {
                subSteps.add(montarStep(sub));
            }
            node.set("steps", subSteps);
            return node;
        }
        if (step.durationSeconds() != null) {
            node.put("duration", step.durationSeconds());
        }
        if (step.distanceMeters() != null) {
            node.put("distance", step.distanceMeters());
        }
        switch (step.meta()) {
            case PaceTarget pace -> node.set("pace", montarPace(pace));
            case HrTarget hr -> node.set("hr", montarHr(hr));
            case IntensityTarget.NoTarget ignored -> {
                // "Sem objetivo": o step vai sem meta, e isso é prescrição válida.
            }
            case null -> {
                // Defensivo: WorkoutStep.simples normaliza null para SEM_OBJETIVO.
            }
        }
        return node;
    }

    private ObjectNode montarPace(PaceTarget pace) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("units", "secs/km");
        if (pace.startSecsPerKm() != null && pace.startSecsPerKm().equals(pace.endSecsPerKm())) {
            node.put("value", pace.startSecsPerKm());
        } else {
            node.put("start", pace.startSecsPerKm());
            node.put("end", pace.endSecsPerKm());
        }
        return node;
    }

    /**
     * Alvo de FC sempre absoluto. As formas relativas do padrão ({@code %hr}, {@code hr_zone}) não
     * são emitidas: a primeira é %FCmax por definição do formato, enquanto o domínio é %LTHR, e a
     * segunda delega a conversão às zonas configuradas no relógio, que o Menthoros não escreve.
     * O {@code HrTarget} já chega resolvido do converter.
     */
    private ObjectNode montarHr(HrTarget hr) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("units", "bpm");
        node.put("start", hr.startBpm());
        node.put("end", hr.endBpm());
        return node;
    }
}
