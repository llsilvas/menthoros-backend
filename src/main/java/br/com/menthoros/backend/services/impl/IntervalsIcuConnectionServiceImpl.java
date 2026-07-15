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
     * {@inheritDoc}
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
                .findByAtletaIdAndPlataforma(atletaId, INTERVALS_ICU)
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
     * {@inheritDoc}
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
     * {@inheritDoc}
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
     * {@inheritDoc}
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
