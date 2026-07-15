package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.intervalsicu.IcuAthleteDto;
import br.com.menthoros.backend.dto.output.IntervalsIcuConnectionStatusDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.IntervalsIcuClient;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * Conexão do atleta com o intervals.icu. Espelha o padrão de soft-disconnect do
 * {@code StravaOAuthServiceImpl}, mas com credencial de API key (não OAuth2) e validação síncrona
 * contra a API externa antes de persistir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntervalsIcuConnectionServiceImpl implements IntervalsIcuConnectionService {

    private static final FonteDados INTERVALS_ICU = FonteDados.INTERVALS_ICU;

    private final IntervalsIcuClient intervalsIcuClient;
    private final IntegracaoExternaRepository integracaoRepository;
    private final AtletaRepository atletaRepository;

    /**
     * Valida a API key contra o intervals.icu e persiste a conexão. Reconecta (reusa o registro
     * existente da unique atleta+plataforma, tenant-scoped) quando o atleta já teve uma conexão.
     *
     * <p>Idempotent: NO — cada chamada revalida a key contra a API externa; uma key inválida
     * lança exceção sem alterar estado, mas uma key válida repetida reescreve o registro existente.
     * <p>Side Effects: chamada HTTP externa (validação da key) + persistência (insert ou update).
     * <p>Tenant-aware: YES — resolve o atleta e a integração via {@code TenantContext.getRequiredTenantId()}.
     *
     * @throws DomainRuleViolationException se a key for inválida (422)
     */
    @Override
    @Transactional
    public IntervalsIcuConnectionStatusDto conectar(UUID atletaId, String apiKey) {
        if (atletaId == null) {
            throw new IllegalArgumentException("atletaId não pode ser nulo");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("apiKey não pode ser vazia");
        }

        log.info("Conectando intervals.icu: atletaId={}", atletaId);
        UUID tenantId = TenantContext.getRequiredTenantId();

        Optional<IcuAthleteDto> athlete = intervalsIcuClient.validarApiKey(apiKey);
        if (athlete.isEmpty()) {
            log.warn("API key intervals.icu inválida: atletaId={}", atletaId);
            throw new DomainRuleViolationException(
                    "API key inválida — verifique em Settings → Developer no intervals.icu");
        }

        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado"));

        IntegracaoExterna integracao = integracaoRepository
                .findByAtletaIdAndPlataformaAndTenantId(atletaId, INTERVALS_ICU, tenantId)
                .orElseGet(IntegracaoExterna::new);

        integracao.setAtleta(atleta);
        integracao.setPlataforma(INTERVALS_ICU);
        integracao.setTenantId(tenantId);
        integracao.setAccessToken(apiKey);
        integracao.setExternalAthleteId(athlete.get().id());
        integracao.setAtivo(true);
        integracao.setLastSyncError(null);

        integracao = integracaoRepository.save(integracao);

        log.info("Conexão intervals.icu criada/atualizada: atletaId={}, externalAthleteId={}",
                atletaId, integracao.getExternalAthleteId());
        return toStatusDto(integracao);
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
     * Soft-disconnect (padrão Strava): mantém o registro histórico, zera as credenciais e desativa.
     *
     * <p>Idempotent: YES — desconectar duas vezes é seguro (já desconectado / nunca conectado = no-op).
     * <p>Side Effects: Database update (ativo=false, accessToken/refreshToken=null) quando existir conexão.
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
                    integracaoRepository.save(integracao);
                    log.info("Conexão intervals.icu desconectada: atletaId={}", atletaId);
                }, () -> log.info("Desconectar intervals.icu: nenhuma conexão encontrada, no-op. atletaId={}", atletaId));
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
