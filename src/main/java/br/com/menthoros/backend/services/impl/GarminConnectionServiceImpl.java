package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.garmin.GarminTokenResponse;
import br.com.menthoros.backend.dto.output.GarminConnectionStatusDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.GarminClient;
import br.com.menthoros.backend.services.GarminConnectionService;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GarminConnectionServiceImpl implements GarminConnectionService {

    private static final FonteDados GARMIN = FonteDados.GARMIN;
    static final String WORKOUT_IMPORT = "WORKOUT_IMPORT";
    static final String ACTIVITY_EXPORT = "ACTIVITY_EXPORT";
    private static final String SEPARADOR_SCOPES = ",";

    private final IntegracaoExternaRepository integracaoRepository;
    private final IntervalsIcuConnectionService intervalsIcuConnectionService;
    private final GarminClient garminClient;
    private final Clock clock;

    @Override
    @Transactional
    public void ativar(Atleta atleta, UUID tenantId, GarminTokenResponse token, String userId,
                       List<String> permissoes) {
        UUID atletaId = atleta.getId();
        Instant agora = clock.instant();

        IntegracaoExterna garmin = integracaoRepository
                .findByAtletaIdAndPlataformaAndTenantId(atletaId, GARMIN, tenantId)
                .orElseGet(IntegracaoExterna::new);
        garmin.setAtleta(atleta);
        garmin.setPlataforma(GARMIN);
        garmin.setTenantId(tenantId);
        garmin.setExternalAthleteId(userId);
        garmin.setAccessToken(token.accessToken());
        garmin.setRefreshToken(token.refreshToken());
        garmin.setTokenExpiraEm(agora.plusSeconds(token.expiresIn()));
        garmin.setRefreshTokenExpiraEm(agora.plusSeconds(token.refreshTokenExpiresInOuPadrao()));
        garmin.setScopes(String.join(SEPARADOR_SCOPES, permissoes));
        garmin.setAtivo(true);
        garmin.setLastSyncError(null);
        integracaoRepository.save(garmin);

        // Um canal por atleta (D4). A linha do intervals.icu fica para trás, inativa e sem credenciais.
        integracaoRepository.findActiveByAtletaIdAndPlataformaAndTenantId(
                        atletaId, FonteDados.INTERVALS_ICU, tenantId)
                .ifPresent(icu -> {
                    zerarCredenciais(icu);
                    integracaoRepository.save(icu);
                    log.info("intervals.icu desativado por exclusividade de canal: atletaId={}", atletaId);
                });

        intervalsIcuConnectionService.pausarStravaAutomaticamente(atletaId, tenantId);
        log.info("Conexão Garmin ativada: atletaId={}", atletaId);
    }

    @Override
    @Transactional
    public void desconectar(UUID atletaId, UUID tenantId) {
        integracaoRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, GARMIN, tenantId)
                .ifPresentOrElse(garmin -> {
                    removerRegistroBestEffort(garmin.getAccessToken());
                    zerarCredenciais(garmin);
                    integracaoRepository.save(garmin);
                    log.info("Conexão Garmin desconectada: atletaId={}", atletaId);
                }, () -> log.info("Desconectar Garmin: nenhuma conexão ativa, no-op. atletaId={}", atletaId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GarminConnectionStatusDto> status(UUID atletaId, UUID tenantId) {
        return integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, GARMIN, tenantId)
                .map(i -> {
                    Set<String> scopes = scopesDe(i.getScopes());
                    return new GarminConnectionStatusDto(i.isAtivo(), mascarar(i.getExternalAthleteId()),
                            scopes.contains(WORKOUT_IMPORT), scopes.contains(ACTIVITY_EXPORT),
                            i.getUltimaSincronizacao(), i.getLastSyncError());
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IntegracaoExterna> conexaoDeCanal(UUID atletaId, UUID tenantId) {
        return integracaoRepository.findActiveByAtletaIdAndPlataformaInAndTenantId(
                        atletaId, List.of(FonteDados.INTERVALS_ICU, GARMIN), tenantId)
                .stream().findFirst();
    }

    private void removerRegistroBestEffort(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return;
        }
        try {
            garminClient.removerRegistro(accessToken);
        } catch (RuntimeException e) {
            // A mensagem do client nunca carrega token; só o tipo vai para o log.
            log.warn("removerRegistro no Garmin falhou (best-effort, desconexão local segue): {}",
                    e.getClass().getSimpleName());
        }
    }

    private static void zerarCredenciais(IntegracaoExterna i) {
        i.setAtivo(false);
        i.setAccessToken(null);
        i.setRefreshToken(null);
        i.setTokenExpiraEm(null);
        i.setRefreshTokenExpiraEm(null);
        i.setScopes(null);
        i.setExternalAthleteId(null);
        i.setLastSyncError(null);
    }

    private static Set<String> scopesDe(String scopes) {
        if (!StringUtils.hasText(scopes)) {
            return Set.of();
        }
        return Arrays.stream(scopes.split(SEPARADOR_SCOPES)).map(String::trim).collect(Collectors.toSet());
    }

    private static String mascarar(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        return userId.length() <= 8 ? "****" : userId.substring(0, 4) + "…" + userId.substring(userId.length() - 4);
    }
}
