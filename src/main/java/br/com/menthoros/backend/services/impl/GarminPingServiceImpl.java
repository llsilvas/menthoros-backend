package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.GarminPing;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.GarminPingStatus;
import br.com.menthoros.backend.exception.GarminPingRegistroException;
import br.com.menthoros.backend.repository.GarminPingRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.GarminPingService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GarminPingServiceImpl implements GarminPingService {

    /** Activity API, Appendix A: tipos de corrida. */
    static final Set<String> TIPOS_CORRIDA = Set.of("RUNNING", "INDOOR_RUNNING", "OBSTACLE_RUN",
            "STREET_RUNNING", "TRACK_RUNNING", "TRAIL_RUNNING", "TREADMILL_RUNNING", "ULTRA_RUN", "VIRTUAL_RUN");

    private static final String UNIQUE_SUMMARY_ID = "uk_garmin_ping_summary_id";

    private final GarminPingRepository pingRepository;
    private final IntegracaoExternaRepository integracaoRepository;

    /**
     * Deliberadamente SEM @Transactional: cada {@code save} roda na própria transação, então o
     * catch do dedup (UNIQUE de {@code summary_id}) é efetivo e um item não desfaz os anteriores.
     * Com a anotação, o commit falharia depois do catch (CLAUDE.md do backend).
     */
    @Override
    public void registrar(List<? extends JsonNode> itens) {
        if (itens == null) {
            return;
        }
        for (JsonNode item : itens) {
            registrarItem(item);
        }
    }

    private void registrarItem(JsonNode item) {
        String userId = texto(item, "userId");
        String summaryId = texto(item, "summaryId");
        String callbackUrl = texto(item, "callbackURL");
        long startTime = item.path("startTimeInSeconds").asLong(0);
        // Validar antes do save: violação de NOT NULL faria o Hibernate logar a linha com o token.
        if (userId == null || summaryId == null || callbackUrl == null || startTime <= 0) {
            log.warn("Ping activityFiles do Garmin sem campo obrigatório; ignorado");
            return;
        }

        Optional<IntegracaoExterna> ativa = integracaoRepository.findActiveByExternalAthleteIdAndPlataforma(
                userId, FonteDados.GARMIN);
        Optional<IntegracaoExterna> conexao = ativa.isPresent() ? ativa
                : integracaoRepository.findFirstByExternalAthleteIdAndPlataformaOrderByAtualizadoEmDesc(
                        userId, FonteDados.GARMIN);
        if (conexao.isEmpty()) {
            // Sem atleta não há tenant_id (NOT NULL): ADR-0013, "200 sem efeito".
            log.info("Ping activityFiles de userId Garmin sem conexão registrada; ignorado");
            return;
        }

        GarminPing ping = novoPing(item, conexao.get(), userId, summaryId, callbackUrl, startTime);
        String motivo = motivoDescarte(ping, ativa.isPresent());
        ping.setStatus(motivo == null ? GarminPingStatus.PENDING : GarminPingStatus.DISCARDED);
        ping.setReason(motivo);
        salvar(ping);
    }

    private GarminPing novoPing(JsonNode item, IntegracaoExterna conexao, String userId, String summaryId,
                                String callbackUrl, long startTime) {
        GarminPing ping = new GarminPing();
        ping.setTenantId(conexao.getTenantId());
        ping.setAthleteId(conexao.getAtleta().getId());
        ping.setGarminUserId(userId);
        ping.setSummaryId(summaryId);
        ping.setActivityId(texto(item, "activityId"));
        ping.setCallbackUrl(callbackUrl);
        ping.setActivityType(texto(item, "activityType"));
        ping.setFileType(texto(item, "fileType"));
        ping.setDeviceName(texto(item, "deviceName"));
        ping.setStartTime(Instant.ofEpochSecond(startTime));
        ping.setManual(item.path("manual").asBoolean(false));
        return ping;
    }

    private static String motivoDescarte(GarminPing ping, boolean conexaoAtiva) {
        if (!conexaoAtiva) {
            return "Conexão Garmin inativa";
        }
        if (!"FIT".equalsIgnoreCase(ping.getFileType())) {
            return "Arquivo não é FIT: " + ping.getFileType();
        }
        if (ping.isManual()) {
            return "Atividade manual no Garmin Connect";
        }
        if (ping.getActivityType() == null || !TIPOS_CORRIDA.contains(ping.getActivityType())) {
            return "Modalidade fora de corrida: " + ping.getActivityType();
        }
        return null;
    }

    private void salvar(GarminPing ping) {
        try {
            pingRepository.save(ping);
            log.info("Ping activityFiles registrado: atletaId={}, status={}", ping.getAthleteId(), ping.getStatus());
        } catch (DataIntegrityViolationException e) {
            if (violouUniqueSummaryId(e)) {
                log.debug("Ping activityFiles repetido; ignorado: atletaId={}", ping.getAthleteId());
                return;
            }
            throw falhaSanitizada(e);
        } catch (RuntimeException e) {
            throw falhaSanitizada(e);
        }
    }

    private static boolean violouUniqueSummaryId(DataIntegrityViolationException e) {
        String mensagem = e.getMostSpecificCause().getMessage();
        return mensagem != null && mensagem.contains(UNIQUE_SUMMARY_ID);
    }

    private static GarminPingRegistroException falhaSanitizada(RuntimeException e) {
        // Só o tipo: a mensagem do banco pode carregar o callback_url com o token.
        log.error("Falha ao gravar ping activityFiles do Garmin: {}", e.getClass().getSimpleName());
        return new GarminPingRegistroException("Falha ao registrar ping activityFiles do Garmin");
    }

    private static String texto(JsonNode item, String campo) {
        String valor = item.path(campo).asText("");
        return valor.isBlank() ? null : valor;
    }
}
