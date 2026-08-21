package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.external.IntervalsIcuProperties;
import br.com.menthoros.backend.dto.intervalsicu.IcuTokenResponse;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.IntervalsIcuClient;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import br.com.menthoros.backend.services.IntervalsIcuOAuthService;
import br.com.menthoros.backend.services.helper.IntervalsIcuStateSigner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fluxo OAuth2 com o intervals.icu (app 663).
 *
 * <p>Ver {@link IntervalsIcuOAuthService} para o contrato e as decisões (D2, D3, D7, D10, D12, D14).
 */
@Slf4j
@Service
public class IntervalsIcuOAuthServiceImpl implements IntervalsIcuOAuthService {

    private static final FonteDados INTERVALS_ICU = FonteDados.INTERVALS_ICU;

    private final IntervalsIcuProperties properties;
    private final IntervalsIcuStateSigner stateSigner;
    private final WebClient intervalsIcuWebClient;
    private final AtletaRepository atletaRepository;
    private final IntegracaoExternaRepository integracaoRepository;
    private final AtletaProgressService atletaProgressService;
    private final IntervalsIcuConnectionService connectionService;
    private final IntervalsIcuClient intervalsIcuClient;

    public IntervalsIcuOAuthServiceImpl(
            IntervalsIcuProperties properties,
            IntervalsIcuStateSigner stateSigner,
            @Qualifier("intervalsIcuWebClient") WebClient intervalsIcuWebClient,
            AtletaRepository atletaRepository,
            IntegracaoExternaRepository integracaoRepository,
            AtletaProgressService atletaProgressService,
            IntervalsIcuConnectionService connectionService,
            IntervalsIcuClient intervalsIcuClient) {
        this.properties = properties;
        this.stateSigner = stateSigner;
        this.intervalsIcuWebClient = intervalsIcuWebClient;
        this.atletaRepository = atletaRepository;
        this.integracaoRepository = integracaoRepository;
        this.atletaProgressService = atletaProgressService;
        this.connectionService = connectionService;
        this.intervalsIcuClient = intervalsIcuClient;
    }

    /**
     * Idempotent: NO — cada chamada gera um state novo.
     * Side Effects: NONE.
     * Tenant-aware: YES — via {@code resolverAtletaIdAtual()}, que exige tenant e atleta vinculado.
     */
    @Override
    @Transactional(readOnly = true)
    public String getAuthorizationUrl() {
        UUID atletaId = atletaProgressService.resolverAtletaIdAtual();

        // build(true) — mesmo padrao do StravaOAuthServiceImpl, que roda em producao. Trata os
        // valores como ja codificados, ou seja, nada e reencodado. Funciona aqui porque nenhum
        // valor tem caractere que exija escape: o scope usa ':' e ',' (validos em query string),
        // o state e base64url, e a redirect-uri nao contem '?' nem '&'. Se a redirect-uri um dia
        // ganhar query propria, isto quebra com erro de match no provedor -- e so o smoke real
        // (task 9.2) pegaria.
        return UriComponentsBuilder.fromUriString(properties.getAuthorizationUri())
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("scope", properties.getScope())
                .queryParam("state", stateSigner.assinar(atletaId))
                .build(true)
                .toUriString();
    }

    /**
     * Idempotent: NO — o {@code code} só é trocável uma vez pelo provedor.
     * Side Effects: chamada HTTP externa + persistência, apenas em {@link Resultado#SUCESSO}.
     * Tenant-aware: YES — tenant vem do atleta resolvido pelo state, nunca do request (CA9).
     *
     * <p><b>A ordem dos passos é normativa, não estilo.</b> O guard de conta já vinculada (passo 5)
     * roda <b>antes</b> de qualquer busca ou mutação de {@link IntegracaoExterna} (passo 6). O
     * motivo é JPA: uma entidade obtida por {@code findBy...} é <i>managed</i>, e mutá-la a
     * persiste no flush do commit <b>sem {@code save()} explícito</b>. Como este método devolve um
     * resultado tipado em vez de lançar (D14 — o callback não pode produzir erro HTTP), o retorno
     * normal <b>commita</b> a transação. Se o guard rodasse depois da mutação, o "nada é
     * persistido" de CA12 dependeria de um rollback que nunca aconteceria.
     */
    @Override
    @Transactional
    public Resultado exchangeCodeForToken(String code, String state) {
        // 1. Valida o state.
        Optional<UUID> atletaIdDoState = stateSigner.validar(state);
        if (atletaIdDoState.isEmpty()) {
            return Resultado.STATE_INVALIDO;
        }
        UUID atletaId = atletaIdDoState.get();

        // 2. Resolve o atleta SEM filtro de tenant — o callback é público e não tem JWT.
        //    Mesmo padrão de StravaOAuthServiceImpl.findAtletaForCallback.
        Optional<Atleta> atletaOpt = atletaRepository.findByIdBasic(atletaId);
        if (atletaOpt.isEmpty()) {
            log.warn("Callback intervals.icu com state válido para atleta inexistente: atletaId={}", atletaId);
            return Resultado.ATLETA_NAO_ENCONTRADO;
        }
        Atleta atleta = atletaOpt.get();
        UUID tenantId = atleta.getAssessoria().getId();

        // 3 e 4. Troca o code por token e lê athlete.id.
        Optional<IcuTokenResponse> tokenOpt = trocarCode(code);
        if (tokenOpt.isEmpty()) {
            return Resultado.FALHA_NA_TROCA;
        }
        IcuTokenResponse token = tokenOpt.get();
        if (!StringUtils.hasText(token.accessToken())
                || token.athlete() == null
                || !StringUtils.hasText(token.athlete().id())) {
            log.warn("Resposta de token do intervals.icu sem access_token ou athlete.id");
            return Resultado.FALHA_NA_TROCA;
        }
        String externalAthleteId = token.athlete().id();

        // 5. Guard D5.1/D12 — ANTES de buscar ou mutar a integração. Ver JavaDoc do método.
        List<IntegracaoExterna> deOutrosAtletas = integracaoRepository
                .findOtherActiveByExternalAthleteIdAndPlataformaAndTenantId(
                        externalAthleteId, INTERVALS_ICU, tenantId, atletaId);
        if (!deOutrosAtletas.isEmpty()) {
            log.error("SECURITY: externalAthleteId {} ja vinculado a outro atleta ativo no tenant {} — "
                            + "conexao do atleta {} recusada ({} conflito(s))",
                    externalAthleteId, tenantId, atletaId, deOutrosAtletas.size());
            return Resultado.CONTA_JA_VINCULADA;
        }

        // 6. Find-or-create e popula.
        IntegracaoExterna integracao = integracaoRepository
                .findByAtletaIdAndPlataformaAndTenantId(atletaId, INTERVALS_ICU, tenantId)
                .orElseGet(IntegracaoExterna::new);

        integracao.setAtleta(atleta);
        integracao.setPlataforma(INTERVALS_ICU);
        integracao.setTenantId(tenantId);
        integracao.setAccessToken(token.accessToken());
        integracao.setScopes(token.scope());
        integracao.setExternalAthleteId(externalAthleteId);
        integracao.setAtivo(true);
        integracao.setLastSyncError(null);
        // refreshToken e tokenExpiraEm ficam nulos DE PROPOSITO (D3): o provedor nao emite
        // refresh_token nem expires_in, e o token nao expira por tempo. Ele morre quando o atleta
        // revoga no intervals.icu. Nao ha refresh flow a implementar aqui -- se voce veio
        // "consertar" estes nulos, o campo que voce procura nao existe na resposta.
        integracaoRepository.save(integracao);

        log.info("Conexao intervals.icu via OAuth criada/atualizada: atletaId={}, externalAthleteId={}",
                atletaId, externalAthleteId);

        // 7. Hook D5.2 — preservado, não reimplementado (D9).
        connectionService.pausarStravaAutomaticamente(atletaId, tenantId);

        return Resultado.SUCESSO;
    }

    /**
     * Idempotent: YES — desconectar duas vezes é seguro.
     * Side Effects: chamada HTTP externa (best-effort) + Database update.
     * Tenant-aware: YES.
     */
    @Override
    @Transactional
    public void revogarEDesconectar(UUID atletaId) {
        if (atletaId == null) {
            throw new IllegalArgumentException("atletaId não pode ser nulo");
        }

        UUID tenantId = TenantContext.getRequiredTenantId();

        // Remoto antes de local, e não o contrário: a desconexão local zera o accessToken, e
        // depois dela não haveria mais com que revogar no provedor (D7).
        connectionService.conexaoAtiva(atletaId, tenantId)
                .map(IntegracaoExterna::getAccessToken)
                .filter(StringUtils::hasText)
                .ifPresent(this::revogarBestEffort);

        connectionService.desconectar(atletaId);
    }

    /**
     * O client já engole a falha (D7), mas um {@code RuntimeException} inesperado daqui abortaria
     * a desconexão local — que é justamente o que o atleta pediu. Segunda rede de proteção.
     */
    private void revogarBestEffort(String token) {
        try {
            intervalsIcuClient.revogarAcesso(token);
        } catch (RuntimeException e) {
            log.warn("Revogacao remota no intervals.icu falhou; desconexao local segue: {}",
                    e.getClass().getSimpleName());
        }
    }

    private Optional<IcuTokenResponse> trocarCode(String code) {
        if (!StringUtils.hasText(code)) {
            log.warn("Callback intervals.icu sem code");
            return Optional.empty();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("code", code);

        try {
            return Optional.ofNullable(intervalsIcuWebClient.post()
                    .uri(properties.getTokenUri())
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(IcuTokenResponse.class)
                    .block());
        } catch (RuntimeException e) {
            // Sem body e sem code na mensagem: o code é credencial de troca e a URL do callback
            // fica visível na barra do browser e no histórico (CA10).
            log.warn("Falha na troca de code por token no intervals.icu: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
