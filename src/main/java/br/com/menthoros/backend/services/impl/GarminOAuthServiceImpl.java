package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.core.CacheConfig;
import br.com.menthoros.backend.config.external.GarminProperties;
import br.com.menthoros.backend.dto.garmin.GarminTokenResponse;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.exception.GarminApiException;
import br.com.menthoros.backend.exception.GarminCanalIndisponivelException;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.GarminClient;
import br.com.menthoros.backend.services.GarminConnectionService;
import br.com.menthoros.backend.services.GarminOAuthService;
import br.com.menthoros.backend.services.helper.GarminStateSigner;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fluxo OAuth2 PKCE com o Garmin (D2, D3, D4 de garmin-connection).
 *
 * <p>Nenhum método abre transação: as chamadas HTTP ao Garmin não podem segurar conexão do pool. A
 * persistência é um único {@code save} por operação, ou o {@code @Transactional} de
 * {@link GarminConnectionService#ativar}.
 */
@Slf4j
@Service
public class GarminOAuthServiceImpl implements GarminOAuthService {

    private static final FonteDados GARMIN = FonteDados.GARMIN;
    private static final String ALFABETO_VERIFIER =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
    private static final int TAMANHO_VERIFIER = 64;
    /** Margem aplicada só na leitura (D3): o valor gravado é a expiração real. */
    static final Duration MARGEM_ACCESS_TOKEN = Duration.ofSeconds(600);

    private final GarminProperties properties;
    private final GarminStateSigner stateSigner;
    private final GarminClient garminClient;
    private final GarminConnectionService connectionService;
    private final AtletaRepository atletaRepository;
    private final IntegracaoExternaRepository integracaoRepository;
    private final CacheManager cacheManager;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public GarminOAuthServiceImpl(GarminProperties properties, GarminStateSigner stateSigner,
                                  GarminClient garminClient, GarminConnectionService connectionService,
                                  AtletaRepository atletaRepository,
                                  IntegracaoExternaRepository integracaoRepository,
                                  CacheManager cacheManager, MeterRegistry meterRegistry, Clock clock) {
        this.properties = properties;
        this.stateSigner = stateSigner;
        this.garminClient = garminClient;
        this.connectionService = connectionService;
        this.atletaRepository = atletaRepository;
        this.integracaoRepository = integracaoRepository;
        this.cacheManager = cacheManager;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @Override
    public String gerarAuthorizeUrl(UUID atletaId) {
        if (!properties.habilitado() || !properties.configurado()) {
            throw new GarminCanalIndisponivelException("Canal Garmin indisponível neste ambiente");
        }
        String verifier = novoVerifier();
        String state = stateSigner.assinar(atletaId);
        pkceCache().put(state, verifier);

        return UriComponentsBuilder.fromUriString(properties.getAuthorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("code_challenge", challengeS256(verifier))
                .queryParam("code_challenge_method", "S256")
                .queryParam("state", state)
                .build().encode().toUriString();
    }

    @Override
    public Resultado concluir(String state, String code) {
        Resultado resultado = executarConclusao(state, code);
        String tag = switch (resultado) {
            case SUCESSO -> "sucesso";
            case DENIED -> "recusado";
            default -> "erro";
        };
        meterRegistry.counter("garmin.oauth.callback", "resultado", tag).increment();
        return resultado;
    }

    private Resultado executarConclusao(String state, String code) {
        Optional<UUID> atletaIdDoState = stateSigner.validar(state);
        if (atletaIdDoState.isEmpty()) {
            return Resultado.STATE_INVALIDO;
        }
        UUID atletaId = atletaIdDoState.get();

        // Consumo atômico: o verifier vale uma vez, mesmo que o resto falhe.
        Object verifier = pkceCache().getNativeCache().asMap().remove(state);
        if (verifier == null) {
            log.warn("Callback Garmin sem verifier PKCE (expirado ou state reutilizado): atletaId={}", atletaId);
            return Resultado.STATE_INVALIDO;
        }
        if (!StringUtils.hasText(code)) {
            log.info("Callback Garmin sem code (recusado pelo atleta): atletaId={}", atletaId);
            return Resultado.DENIED;
        }

        Optional<Atleta> atletaOpt = atletaRepository.findByIdBasic(atletaId);
        if (atletaOpt.isEmpty()) {
            log.warn("Callback Garmin com state válido para atleta inexistente: atletaId={}", atletaId);
            return Resultado.ATLETA_NAO_ENCONTRADO;
        }
        Atleta atleta = atletaOpt.get();
        UUID tenantId = atleta.getAssessoria().getId();

        GarminTokenResponse token;
        String userId;
        List<String> permissoes;
        try {
            token = garminClient.trocarCode(code, (String) verifier);
            userId = garminClient.userId(token.accessToken());
            permissoes = garminClient.permissoes(token.accessToken());
        } catch (GarminApiException e) {
            log.warn("Falha na troca de code no Garmin: status={}", e.getStatus() != null ? e.getStatus().value() : "transporte");
            return Resultado.FALHA_NA_TROCA;
        }

        // Guard global (sem tenant): o client ID é um só e o webhook roteia por userId (D4).
        boolean vinculadoAOutro = integracaoRepository
                .findActiveByExternalAthleteIdAndPlataforma(userId, GARMIN)
                .filter(i -> !i.getAtleta().getId().equals(atletaId))
                .isPresent();
        if (vinculadoAOutro) {
            log.error("SECURITY: userId Garmin já vinculado a outro atleta ativo — conexão do atleta {} recusada",
                    atletaId);
            return Resultado.CONTA_JA_VINCULADA;
        }

        connectionService.ativar(atleta, tenantId, token, userId, permissoes);
        return Resultado.SUCESSO;
    }

    @Override
    public String tokenValido(IntegracaoExterna conexao) {
        boolean vigente = conexao.getTokenExpiraEm() != null
                && conexao.getTokenExpiraEm().minus(MARGEM_ACCESS_TOKEN).isAfter(clock.instant());
        return vigente ? conexao.getAccessToken() : renovar(conexao);
    }

    @Override
    public String renovar(IntegracaoExterna conexao) {
        GarminTokenResponse novo;
        try {
            novo = garminClient.renovar(conexao.getRefreshToken());
        } catch (GarminApiException e) {
            int status = e.getStatus() != null ? e.getStatus().value() : 0;
            meterRegistry.counter("garmin.token.refresh", "resultado", "falha").increment();
            if (status == 400 || status == 401) {
                conexao.setAtivo(false);
                conexao.setAccessToken(null);
                conexao.setRefreshToken(null);
                conexao.setTokenExpiraEm(null);
                conexao.setRefreshTokenExpiraEm(null);
                conexao.setLastSyncError("Autorização Garmin revogada ou expirada; reconecte a conta");
                integracaoRepository.save(conexao);
                log.warn("Refresh Garmin recusado ({}): conexão marcada inativa, atletaId={}", status,
                        conexao.getAtleta().getId());
                throw new IllegalStateException("Conexão Garmin expirada. O atleta precisa reconectar.");
            }
            throw e;
        }

        var agora = clock.instant();
        conexao.setAccessToken(novo.accessToken());
        if (StringUtils.hasText(novo.refreshToken())) {
            conexao.setRefreshToken(novo.refreshToken());
        }
        conexao.setTokenExpiraEm(agora.plusSeconds(novo.expiresIn()));
        conexao.setRefreshTokenExpiraEm(agora.plusSeconds(novo.refreshTokenExpiresInOuPadrao()));
        integracaoRepository.save(conexao);
        meterRegistry.counter("garmin.token.refresh", "resultado", "sucesso").increment();
        return novo.accessToken();
    }

    private CaffeineCache pkceCache() {
        return (CaffeineCache) cacheManager.getCache(CacheConfig.GARMIN_PKCE);
    }

    private String novoVerifier() {
        StringBuilder sb = new StringBuilder(TAMANHO_VERIFIER);
        for (int i = 0; i < TAMANHO_VERIFIER; i++) {
            sb.append(ALFABETO_VERIFIER.charAt(random.nextInt(ALFABETO_VERIFIER.length())));
        }
        return sb.toString();
    }

    private static String challengeS256(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
