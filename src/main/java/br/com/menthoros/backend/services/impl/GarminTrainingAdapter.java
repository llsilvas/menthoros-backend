package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.domain.workout.StructuredWorkout;
import br.com.menthoros.backend.dto.garmin.GarminSchedule;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.GarminApiException;
import br.com.menthoros.backend.services.GarminClient;
import br.com.menthoros.backend.services.GarminOAuthService;
import br.com.menthoros.backend.services.PushResult;
import br.com.menthoros.backend.services.WorkoutChannel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;

/**
 * Adapter do canal direto Garmin (Training API V2). O {@code externalId} do treino guarda o
 * {@code workoutId}; o {@code scheduleId} não é guardado e é achado por {@code workoutId} + data
 * (design D3). Nunca lança: erros viram {@link PushResult#erro}, com mensagem curada que não
 * inclui token.
 *
 * <p>Os chamadores não o injetam: usam {@link WorkoutChannelPorPlataforma}, que é o {@code @Primary}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GarminTrainingAdapter implements WorkoutChannel {

    // shortcut: a janela onde se procura o schedule de um treino que mudou de data é fixa em ±14 dias,
    // porque o adapter não conhece a janela do plano; ampliar se uma re-aprovação mover treinos além disso.
    private static final int JANELA_BUSCA_DIAS = 14;
    private static final String ORIGEM_MENTHOROS = GarminWorkoutPayloadMapper.PROVEDOR;

    private final GarminClient client;
    private final GarminOAuthService oauth;
    private final GarminWorkoutPayloadMapper payloadMapper;

    /**
     * Idempotent: NO — sem id armazenado cria workout e schedule novos; com id atualiza.
     * Side Effects: External API call (POST/GET/PUT workout, POST/PUT/GET schedule); renova o token em 401.
     * Tenant-aware: NO — a credencial é do atleta (conexao).
     */
    @Override
    public PushResult push(IntegracaoExterna conexao, StructuredWorkout workout, Long eventIdArmazenado) {
        if (conexao == null) {
            throw new IllegalArgumentException("conexao não pode ser nula");
        }
        if (workout == null) {
            throw new IllegalArgumentException("workout não pode ser nulo");
        }
        JsonNode payload;
        try {
            payload = payloadMapper.montar(workout);
        } catch (DomainRuleViolationException e) {
            return PushResult.erro(StatusSincronizacao.ERRO_VALIDACAO, e.getMessage());
        }

        try {
            String token = oauth.tokenValido(conexao);
            try {
                return enviar(token, workout.scheduledDate(), payload, eventIdArmazenado);
            } catch (GarminApiException e) {
                if (!temStatus(e, 401)) {
                    throw e;
                }
                return enviar(oauth.renovar(conexao), workout.scheduledDate(), payload, eventIdArmazenado);
            }
        } catch (IllegalStateException e) {
            return PushResult.erro(StatusSincronizacao.ERRO_AUTENTICACAO,
                    "Reautorize a conexão com o Garmin Connect");
        } catch (GarminApiException e) {
            return PushResult.erro(mapearStatus(e), mensagemCurada(e));
        } catch (Exception e) {
            log.warn("Falha inesperada ao sincronizar treino com o Garmin: {}", e.getMessage());
            return PushResult.erro(StatusSincronizacao.ERRO_TEMPORARIO,
                    "Erro inesperado ao sincronizar treino com o Garmin Connect");
        }
    }

    /**
     * Idempotent: YES — apagar duas vezes é seguro (404 tolerado).
     * Side Effects: External API call (GET schedule; GET workout por schedule fora do conjunto;
     * DELETE schedule e workout dos órfãos do Menthoros).
     * Tenant-aware: NO — a credencial é do atleta (conexao).
     *
     * <p>Só apaga workout com {@code workoutProvider == "Menthoros"}: treino criado pelo atleta no
     * Garmin Connect nunca é tocado. Best-effort: falha num órfão é logada e não aborta os demais.
     */
    @Override
    public void removerOrfaos(IntegracaoExterna conexao, LocalDate inicio, LocalDate fim,
                               Set<String> externalIdsAtuais, Set<String> idsArmazenadosAtuais) {
        if (conexao == null) {
            throw new IllegalArgumentException("conexao não pode ser nula");
        }
        Set<String> atuais = idsArmazenadosAtuais == null ? Set.of() : idsArmazenadosAtuais;
        try {
            String token = oauth.tokenValido(conexao);
            for (GarminSchedule schedule : client.listarSchedules(token, inicio, fim)) {
                if (schedule.workoutId() == null || atuais.contains(String.valueOf(schedule.workoutId()))) {
                    continue;
                }
                removerSeDoMenthoros(token, schedule);
            }
        } catch (Exception e) {
            log.warn("Falha ao reconciliar órfãos do Garmin Connect: {}", e.getMessage());
        }
    }

    /** No-op: sem uploader intermediário não há debounce a contornar (design D7). */
    @Override
    public void tocarEvento(IntegracaoExterna conexao, long eventId, String externalIdCanonico) {
        // intencionalmente vazio
    }

    private PushResult enviar(String token, LocalDate data, JsonNode payload, Long workoutIdArmazenado) {
        if (workoutIdArmazenado == null) {
            return criar(token, data, payload);
        }
        try {
            client.atualizarWorkout(token, workoutIdArmazenado, corpoDeAtualizacao(token, workoutIdArmazenado, payload));
        } catch (GarminApiException e) {
            if (!temStatus(e, 404)) {
                throw e;
            }
            // O atleta apagou o workout no Garmin Connect: o id guardado morreu, cria de novo.
            return criar(token, data, payload);
        }
        garantirSchedule(token, workoutIdArmazenado, data);
        return PushResult.okAtualizado(workoutIdArmazenado);
    }

    /**
     * O PUT exige {@code ownerId} (Training API §3.1), que o Menthoros não guarda: vem do workout
     * atual. Sem um {@code ownerId} escalar o PUT seria recusado de qualquer jeito, então falha antes
     * (sem status: vira erro temporário e entra no retry).
     */
    private ObjectNode corpoDeAtualizacao(String token, Long workoutId, JsonNode payload) {
        JsonNode atual = client.buscarWorkout(token, workoutId);
        JsonNode ownerId = atual == null ? null : atual.get("ownerId");
        if (ownerId == null || !ownerId.isValueNode() || ownerId.isNull()) {
            throw new GarminApiException(null, "Workout " + workoutId + " veio do Garmin sem ownerId");
        }
        ObjectNode corpo = payload.deepCopy();
        corpo.put("workoutId", workoutId);
        corpo.set("ownerId", ownerId);
        return corpo;
    }

    private PushResult criar(String token, LocalDate data, JsonNode payload) {
        Long workoutId = client.criarWorkout(token, payload);
        try {
            client.criarSchedule(token, workoutId, data);
        } catch (GarminApiException e) {
            // Sem o schedule o treino não aparece no relógio, e o id não é guardado: o retry criaria
            // um segundo workout. Apaga este para não deixar duplicata.
            apagarWorkoutSemErro(token, workoutId);
            throw e;
        }
        return PushResult.okCriado(workoutId);
    }

    /** Garante um schedule do workout na data: mantém, move ou recria. */
    private void garantirSchedule(String token, Long workoutId, LocalDate data) {
        if (client.listarSchedules(token, data, data).stream().anyMatch(s -> workoutId.equals(s.workoutId()))) {
            return;
        }
        GarminSchedule existente = client
                .listarSchedules(token, data.minusDays(JANELA_BUSCA_DIAS), data.plusDays(JANELA_BUSCA_DIAS))
                .stream()
                .filter(s -> workoutId.equals(s.workoutId()))
                .findFirst()
                .orElse(null);
        if (existente != null) {
            client.atualizarSchedule(token, existente.scheduleId(), workoutId, data);
        } else {
            client.criarSchedule(token, workoutId, data);
        }
    }

    private void removerSeDoMenthoros(String token, GarminSchedule schedule) {
        try {
            JsonNode workout = client.buscarWorkout(token, schedule.workoutId());
            if (!ORIGEM_MENTHOROS.equals(workout.path("workoutProvider").asText())) {
                return;
            }
            ignorando404(() -> client.deletarSchedule(token, schedule.scheduleId()));
            ignorando404(() -> client.deletarWorkout(token, schedule.workoutId()));
        } catch (GarminApiException e) {
            if (!temStatus(e, 404)) {
                log.warn("Falha ao remover órfão {} do Garmin Connect: {}", schedule.workoutId(), e.getMessage());
            }
        }
    }

    private static void ignorando404(Runnable acao) {
        try {
            acao.run();
        } catch (GarminApiException e) {
            if (!temStatus(e, 404)) {
                throw e;
            }
        }
    }

    private void apagarWorkoutSemErro(String token, Long workoutId) {
        try {
            client.deletarWorkout(token, workoutId);
        } catch (GarminApiException e) {
            log.warn("Não foi possível apagar o workout {} após falha no schedule: {}", workoutId, e.getMessage());
        }
    }

    private static boolean temStatus(GarminApiException e, int status) {
        return e.getStatus() != null && e.getStatus().value() == status;
    }

    private static StatusSincronizacao mapearStatus(GarminApiException e) {
        if (e.getStatus() == null) {
            return StatusSincronizacao.ERRO_TEMPORARIO;
        }
        return switch (e.getStatus().value()) {
            case 401, 403, 412 -> StatusSincronizacao.ERRO_AUTENTICACAO;
            case 400 -> StatusSincronizacao.ERRO_VALIDACAO;
            case 429 -> StatusSincronizacao.ERRO_LIMITE_RATE;
            default -> StatusSincronizacao.ERRO_TEMPORARIO;
        };
    }

    private static String mensagemCurada(GarminApiException e) {
        if (e.getStatus() == null) {
            return "Erro de comunicação com o Garmin Connect";
        }
        return switch (e.getStatus().value()) {
            case 403, 412 -> "Autorize o envio de treinos no Garmin Connect";
            case 401 -> "Reautorize a conexão com o Garmin Connect";
            case 400 -> "O Garmin Connect recusou o treino (dados inválidos)";
            case 429 -> "Limite de requisições do Garmin Connect atingido; nova tentativa automática";
            default -> "Erro temporário ao sincronizar com o Garmin Connect";
        };
    }
}
