package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.IntervalsIcuConnectionStatusDto;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulta, desconexão e hook de pausa do Strava para a conexão do atleta com o intervals.icu.
 * Espelha o soft-disconnect do {@code StravaOAuthServiceImpl}.
 *
 * <p>A <b>criação</b> da conexão não mora mais aqui: com a remoção do fluxo de API key (D6), quem
 * persiste o vínculo é o {@code IntervalsIcuOAuthService}, a partir do callback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntervalsIcuConnectionServiceImpl implements IntervalsIcuConnectionService {

    private static final FonteDados INTERVALS_ICU = FonteDados.INTERVALS_ICU;

    private final IntegracaoExternaRepository integracaoRepository;

    /**
     * Hook D5.2 (decisão do founder — pausa automática, não mais um passo manual primário): ao
     * conectar intervals.icu com Strava já ativo, pausa a sincronização automática do Strava
     * daquele atleta para eliminar a colisão cross-fonte na origem. Sem Strava conectado, é um
     * no-op. Se já pausado (manualmente ou por conexão anterior), não salva de novo.
     *
     * <p>Público desde a remoção do fluxo de API key: quem cria a conexão passou a ser o
     * {@code IntervalsIcuOAuthService}, e D9 manda preservar este hook em vez de reimplementá-lo.
     *
     * <p>Idempotent: YES — pausar duas vezes é no-op na segunda.
     * <p>Side Effects: Database update na linha do Strava, só quando havia Strava ativo não pausado.
     * <p>Tenant-aware: YES — tenant recebido explicitamente por parâmetro.
     */
    @Override
    @Transactional
    public void pausarStravaAutomaticamente(UUID atletaId, UUID tenantId) {
        integracaoRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId)
                .filter(strava -> !strava.isAutoSyncPausado())
                .ifPresent(strava -> {
                    strava.setAutoSyncPausado(true);
                    integracaoRepository.save(strava);
                    log.info("Strava pausado automaticamente (intervals.icu conectado): atletaId={}", atletaId);
                });
    }

    /**
     * Status atual da conexão intervals.icu do atleta — {@link Optional#empty()} quando nunca conectou.
     *
     * <p>Idempotent: YES — leitura pura.
     * <p>Side Effects: NONE.
     * <p>Tenant-aware: YES — query tenant-scoped via {@code TenantContext.getRequiredTenantId()}.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<IntervalsIcuConnectionStatusDto> status(UUID atletaId) {
        if (atletaId == null) {
            throw new IllegalArgumentException("atletaId não pode ser nulo");
        }

        UUID tenantId = TenantContext.getRequiredTenantId();
        return integracaoRepository
                .findByAtletaIdAndPlataformaAndTenantId(atletaId, INTERVALS_ICU, tenantId)
                .map(this::toStatusDto);
    }

    /**
     * Soft-disconnect: mantém o registro histórico, zera <b>todas</b> as credenciais e desativa.
     *
     * <p><b>Por que limpa mais campos que a versão anterior (D13):</b> antes só {@code accessToken}
     * e {@code refreshToken} eram zerados. Com OAuth isso passou a ter consequência mensurável — a
     * métrica de sucesso desta change conta conexões por {@code scopes != null}, então um atleta
     * desconectado continuaria contado como conectado. {@code externalAthleteId} também sai: uma
     * row inativa guardando o id externo é um vínculo sem função que só confunde auditoria (e o
     * guard D12 filtra por conexões ativas, então mantê-lo não protegeria nada).
     *
     * <p>Idempotent: YES — desconectar duas vezes é seguro (já desconectado / nunca conectado = no-op).
     * <p>Side Effects: Database update (ativo=false + credenciais nulas) quando existir conexão.
     * <p>Tenant-aware: YES — query tenant-scoped via {@code TenantContext.getRequiredTenantId()}.
     */
    @Override
    @Transactional
    public void desconectar(UUID atletaId) {
        if (atletaId == null) {
            throw new IllegalArgumentException("atletaId não pode ser nulo");
        }

        log.info("Desconectando intervals.icu: atletaId={}", atletaId);
        UUID tenantId = TenantContext.getRequiredTenantId();

        integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, INTERVALS_ICU, tenantId)
                .ifPresentOrElse(integracao -> {
                    integracao.setAtivo(false);
                    integracao.setAccessToken(null);
                    integracao.setRefreshToken(null);
                    integracao.setScopes(null);
                    integracao.setTokenExpiraEm(null);
                    integracao.setExternalAthleteId(null);
                    integracao.setLastSyncError(null);
                    integracaoRepository.save(integracao);
                    log.info("Conexão intervals.icu desconectada: atletaId={}", atletaId);
                    logStravaPermaneceStrava(atletaId, tenantId);
                }, () -> log.info("Desconectar intervals.icu: nenhuma conexão encontrada, no-op. atletaId={}", atletaId));
    }

    /**
     * D5.2 (decisão do founder — "nunca auto-retomar"): desconectar o intervals.icu NÃO reverte a
     * pausa do Strava — apenas uma leitura para log, nenhuma escrita na linha Strava. Mitigação
     * mínima de observabilidade para um risco residual aceito: sem isso, o coach não tem sinal
     * algum de que o Strava segue pausado até chamar {@code retomar-sync} manualmente.
     */
    private void logStravaPermaneceStrava(UUID atletaId, UUID tenantId) {
        integracaoRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId)
                .filter(IntegracaoExterna::isAutoSyncPausado)
                .ifPresent(strava -> log.info(
                        "Strava permanece autoSyncPausado=true após desconectar intervals.icu (decisão: nunca "
                                + "auto-retomar) — requer retomar-sync manual para reativar: atletaId={}", atletaId));
    }

    /**
     * Conexão ativa do atleta, para colaboradores internos (ex.: listener de push) que já possuem
     * o tenant resolvido e não devem depender do {@code TenantContext} de request.
     *
     * <p>Idempotent: YES — leitura pura.
     * <p>Side Effects: NONE.
     * <p>Tenant-aware: YES — tenant recebido explicitamente por parâmetro (não via TenantContext).
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<IntegracaoExterna> conexaoAtiva(UUID atletaId, UUID tenantId) {
        if (atletaId == null) {
            throw new IllegalArgumentException("atletaId não pode ser nulo");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId não pode ser nulo");
        }

        return integracaoRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, INTERVALS_ICU, tenantId);
    }

    private IntervalsIcuConnectionStatusDto toStatusDto(IntegracaoExterna integracao) {
        LocalDateTime criadoEm = integracao.getCriadoEm();
        return new IntervalsIcuConnectionStatusDto(
                integracao.isAtivo(),
                integracao.getExternalAthleteId(),
                criadoEm != null ? criadoEm.atZone(ZoneId.systemDefault()).toInstant() : null,
                integracao.getUltimaSincronizacao(),
                integracao.getLastSyncError()
        );
    }
}
