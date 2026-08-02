package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.IntervalsIcuApiException;
import br.com.menthoros.backend.exception.IntervalsIcuRateLimitException;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.IntervalsIcuActivityIngestionService;
import br.com.menthoros.backend.services.IntervalsIcuClient;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import br.com.menthoros.backend.services.helper.IntervalsIcuActivityMapper;
import br.com.menthoros.backend.services.helper.IntervalsIcuActivityPersister;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Importa uma atividade específica do intervals.icu como {@link TreinoRealizado} (design.md D3).
 *
 * <p>Orquestrador NÃO transacional — a chamada HTTP externa (passo 3) fica fora de qualquer
 * transação; a persistência + reconciliação (passos 6-9) é delegada ao colaborador transacional
 * {@link IntervalsIcuActivityPersister} (mesmo padrão de separação de {@code
 * IntervalsIcuPushProcessor}, achado da change de hardening: nunca segurar conexão de banco
 * durante IO externo).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntervalsIcuActivityIngestionServiceImpl implements IntervalsIcuActivityIngestionService {

    private static final FonteDados INTERVALS_ICU = FonteDados.INTERVALS_ICU;
    private static final FonteDados STRAVA = FonteDados.STRAVA;

    /** Id opaco do intervals.icu: alfanumérico simples, ou URL completa (extrai o segmento final). */
    private static final Pattern ACTIVITY_ID_VALIDO = Pattern.compile("^[A-Za-z0-9_-]+$");

    private final IntervalsIcuConnectionService intervalsIcuConnectionService;
    private final IntegracaoExternaRepository integracaoExternaRepository;
    private final AtletaRepository atletaRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final IntervalsIcuClient intervalsIcuClient;
    private final IntervalsIcuActivityMapper intervalsIcuActivityMapper;
    private final TreinoMapper treinoMapper;
    private final IntervalsIcuActivityPersister persister;

    @Override
    public TreinoRealizadoOutputDto importarAtividade(UUID atletaId, String activityId, UUID tenantId) {
        if (atletaId == null) {
            throw new IllegalArgumentException("atletaId não pode ser nulo");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId não pode ser nulo");
        }
        String normalizedActivityId = normalizarActivityId(activityId);

        // Passo 0: guarda de idempotência — leitura pura, sem custo de rede. Se a activity já foi
        // importada antes, retorna imediatamente (CA2) — tem prioridade sobre a precondição
        // Strava (achado MÉDIO da 3ª rodada de pre-mortem).
        Optional<TreinoRealizado> existente = treinoRealizadoRepository
                .findByTenantIdAndFonteDadosAndExternalId(tenantId, INTERVALS_ICU, normalizedActivityId);
        if (existente.isPresent()) {
            log.info("Activity intervals.icu já importada: activityId={}, atletaId={}", normalizedActivityId, atletaId);
            return treinoMapper.toOutputDto(existente.get());
        }

        // Passo 1: precondição de pausa Strava (D5.2 — safety net residual, ver design.md).
        verificarPrecondicaoStrava(atletaId, tenantId);

        // Passo 2: conexão ativa + atleta resolvido explicitamente (pre-mortem #15).
        IntegracaoExterna conexao = intervalsIcuConnectionService.conexaoAtiva(atletaId, tenantId)
                .orElseThrow(() -> new DomainConflictException("Atleta não tem conexão intervals.icu ativa"));
        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado"));

        // D5.1: externalAthleteId duplicado no tenant (cadastro incorreto).
        List<IntegracaoExterna> outras = integracaoExternaRepository
                .findOtherActiveByExternalAthleteIdAndPlataformaAndTenantId(
                        conexao.getExternalAthleteId(), INTERVALS_ICU, tenantId, atletaId);
        if (!outras.isEmpty()) {
            log.error("SECURITY: externalAthleteId {} duplicado no tenant {} — atleta {} e outro(s) {}",
                    conexao.getExternalAthleteId(), tenantId, atletaId, outras.size());
            throw new DomainConflictException("Credencial intervals.icu já vinculada a outro atleta neste tenant");
        }

        // Passo 3: chamada externa (fora de qualquer transação).
        IcuActivityDto dto = buscarAtividade(conexao, normalizedActivityId);

        // Passo 4: defesa em profundidade — não vazar existência de activity de outro atleta.
        if (!java.util.Objects.equals(dto.athleteId(), conexao.getExternalAthleteId())) {
            throw new DomainNotFoundException("Activity não encontrada");
        }

        // Passo 5: filtro de modalidade (CA6).
        if (!intervalsIcuActivityMapper.isModalidadeSuportada(dto.type())) {
            throw new DomainRuleViolationException("Modalidade não suportada para import intervals.icu: " + dto.type());
        }

        // Passos 6-9: persistência + reconciliação, em transação própria do colaborador.
        TreinoRealizado salvo = persister.persistir(dto, atleta, tenantId, normalizedActivityId);
        return treinoMapper.toOutputDto(salvo);
    }

    private void verificarPrecondicaoStrava(UUID atletaId, UUID tenantId) {
        integracaoExternaRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, STRAVA, tenantId)
                .filter(strava -> !strava.isAutoSyncPausado())
                .ifPresent(strava -> {
                    throw new DomainConflictException(
                            "pause a sincronização Strava deste atleta antes de importar do intervals.icu");
                });
    }

    private IcuActivityDto buscarAtividade(IntegracaoExterna conexao, String activityId) {
        try {
            return intervalsIcuClient.buscarAtividade(conexao.getAccessToken(), activityId, true);
        } catch (IntervalsIcuApiException e) {
            HttpStatusCode status = e.getStatus();
            if (status == null) {
                throw new IntervalsIcuRateLimitException("Falha de transporte ao buscar atividade — tente novamente mais tarde");
            }
            int code = status.value();
            if (code == 401 || code == 403) {
                conexao.setLastSyncError("Credencial intervals.icu inválida ou revogada");
                integracaoExternaRepository.save(conexao);
                throw new DomainConflictException("Credencial intervals.icu inválida — reconecte a integração");
            }
            if (code == 404) {
                throw new DomainNotFoundException("Activity não encontrada no intervals.icu");
            }
            if (code == 422) {
                throw new DomainRuleViolationException("intervals.icu rejeitou a atividade: " + e.getMessage());
            }
            throw new IntervalsIcuRateLimitException("intervals.icu indisponível — tente novamente mais tarde");
        }
    }

    /**
     * Aceita a URL completa colada do intervals.icu (extrai o segmento final) ou o id simples.
     * Rejeita valores com {@code /}, {@code ?}, {@code %} que não sejam uma URL reconhecível.
     */
    private String normalizarActivityId(String activityId) {
        if (activityId == null || activityId.isBlank()) {
            throw new IllegalArgumentException("activityId não pode ser vazio");
        }
        String trimmed = activityId.trim();
        if (ACTIVITY_ID_VALIDO.matcher(trimmed).matches()) {
            return trimmed;
        }
        if (trimmed.startsWith("https://intervals.icu/") || trimmed.startsWith("http://intervals.icu/")) {
            String semQuery = trimmed.split("[?#]", 2)[0];
            String[] segmentos = semQuery.split("/");
            String ultimo = segmentos[segmentos.length - 1];
            if (ACTIVITY_ID_VALIDO.matcher(ultimo).matches()) {
                return ultimo;
            }
        }
        throw new IllegalArgumentException("activityId inválido: " + activityId);
    }
}
