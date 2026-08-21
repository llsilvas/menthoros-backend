package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.services.IntervalsIcuOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Callback público do fluxo OAuth2 com o intervals.icu.
 *
 * <p><b>Fora do prefixo {@code /api/v1/integracoes/me/**} de propósito (D5):</b> aquele prefixo
 * resolve o atleta pelo JWT, e aqui não há JWT — quem chega é o browser do atleta, redirecionado
 * pelo provedor. A identidade vem do {@code state} assinado. Manter os dois sob o mesmo prefixo
 * obrigaria a abrir um {@code permitAll} dentro de uma árvore inteiramente autenticada.
 *
 * <p><b>Esta classe nunca devolve 4xx/5xx (D14, CA13).</b> Quem está do outro lado é uma pessoa
 * num browser, não um cliente de API: um erro HTTP a deixaria numa página de erro em vez de
 * devolvê-la ao Menthoros. O molde {@code StravaAuthController.callback} <b>não serve aqui</b> —
 * ele faz {@code UUID.fromString(state)} sem try/catch, então um state malformado estoura e o
 * handler global responde com erro. Todo caminho abaixo termina em 302.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/integracoes/intervals-icu")
@Tag(name = "intervals-icu-callback", description = "Callback público do OAuth2 com o intervals.icu")
public class IntervalsIcuCallbackController {

    private static final String PARAM_RETORNO = "intervals-icu";
    private static final String SUCESSO = "success";
    private static final String ERRO = "error";

    /**
     * Rota do front onde o card de conexão vive, dentro do hash. O {@code #} é obrigatório: o
     * front usa {@code createHashRouter}, então tudo que vier antes dele é ignorado pelo roteador.
     */
    private static final String ROTA_PERFIL_ATLETA = "/#/athlete/profile";

    private final IntervalsIcuOAuthService oauthService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    // Sem @PreAuthorize: chamado pelo browser do atleta após o consentimento no provedor, sem JWT.
    // Público por config (CoreSecurityProperties.intervalsIcuPaths -> permitAll); a validação de
    // identidade é a assinatura HMAC do state, não a sessão.
    @GetMapping("/callback")
    @Operation(summary = "Processa o retorno do consentimento OAuth2 do intervals.icu")
    @ApiResponses({
        @ApiResponse(responseCode = "302", description = "Sempre — redireciona ao front com success ou error")
    })
    public ResponseEntity<Void> callback(
            @Parameter(description = "Código de autorização (expira em 2 minutos)")
            @RequestParam(value = "code", required = false) String code,
            @Parameter(description = "State assinado emitido pelo Menthoros")
            @RequestParam(value = "state", required = false) String state,
            @Parameter(description = "Preenchido pelo provedor quando o atleta nega o consentimento")
            @RequestParam(value = "error", required = false) String error) {

        if (error != null) {
            // access_denied é o caso normal de quem clicou "cancelar" — não é incidente.
            log.info("Consentimento intervals.icu não concedido: {}", error);
            return redirecionar(ERRO);
        }

        try {
            IntervalsIcuOAuthService.Resultado resultado = oauthService.exchangeCodeForToken(code, state);
            if (resultado == IntervalsIcuOAuthService.Resultado.SUCESSO) {
                return redirecionar(SUCESSO);
            }
            log.warn("Callback intervals.icu não concluiu a conexão: {}", resultado);
            return redirecionar(ERRO);
        } catch (RuntimeException e) {
            // O service já devolve resultado tipado em vez de lançar; este catch existe para o
            // que ele não previu. Sem ele, um bug inesperado viraria erro HTTP na cara do atleta
            // e violaria CA13. Só a classe da exceção vai ao log: a mensagem poderia carregar o
            // code, que é credencial de troca (CA10).
            log.error("Falha inesperada no callback intervals.icu: {}", e.getClass().getSimpleName(), e);
            return redirecionar(ERRO);
        }
    }

    /**
     * Redireciona para <b>dentro do hash</b> da rota do perfil do atleta, e não para a raiz do
     * front com um query param solto.
     *
     * <p><b>Por que isso importa:</b> o front usa {@code createHashRouter}. Um redirect para
     * {@code FRONTEND_URL?intervals-icu=success} deixaria o hash vazio — o router mandaria o atleta
     * para a rota raiz em vez da tela de onde ele saiu — e o parâmetro ficaria <b>antes</b> do
     * {@code #}, invisível para o {@code useSearchParams}, que lê o query string de dentro do hash.
     * O resultado seria um fluxo que "funciona" e não mostra nada ao atleta.
     *
     * <p>O fluxo do Strava tem esse mesmo defeito ({@code ?strava=status} na raiz) e ninguém
     * percebeu porque o front nunca chegou a tratar aquele retorno. Corrigi-lo é fora do escopo
     * desta change.
     *
     * <p><b>Acoplamento assumido:</b> o path {@link #ROTA_PERFIL_ATLETA} precisa existir no front.
     * Se a rota mudar lá, o atleta cai numa tela vazia — só um teste de ponta a ponta pega.
     */
    private ResponseEntity<Void> redirecionar(String status) {
        String base = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;

        String destino = base + ROTA_PERFIL_ATLETA + "?" + PARAM_RETORNO + "=" + status;

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, destino)
                .build();
    }
}
