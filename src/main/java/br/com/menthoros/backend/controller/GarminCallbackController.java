package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.exception.GarminCanalIndisponivelException;
import br.com.menthoros.backend.services.GarminOAuthService;
import io.swagger.v3.oas.annotations.Operation;
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

/**
 * Callback público do OAuth2 com o Garmin. Mesmo contrato do intervals.icu: nunca devolve 4xx/5xx,
 * todo caminho termina em 302 para dentro do hash do perfil do atleta (o front usa hash router).
 * Identidade pelo state assinado, não pela sessão.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/integracoes/garmin")
@Tag(name = "garmin-callback", description = "Callback público do OAuth2 com o Garmin")
public class GarminCallbackController {

    private static final String PARAM_RETORNO = "garmin";
    private static final String ROTA_PERFIL_ATLETA = "/#/athlete/profile";

    private final GarminOAuthService oauthService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    // Sem @PreAuthorize: browser do atleta redirecionado pelo Garmin, sem JWT (CoreSecurityProperties.garminPaths).
    @GetMapping("/callback")
    @Operation(summary = "Processa o retorno do consentimento OAuth2 do Garmin")
    @ApiResponses({
        @ApiResponse(responseCode = "302", description = "Sempre — redireciona ao front com success, denied ou error")
    })
    public ResponseEntity<Void> callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error) {
        try {
            // Com error (atleta recusou) o code é descartado: o service consome o verifier e devolve DENIED.
            GarminOAuthService.Resultado resultado = oauthService.concluir(state, error != null ? null : code);
            return switch (resultado) {
                case SUCESSO -> redirecionar("success");
                case DENIED -> redirecionar("denied");
                default -> {
                    log.warn("Callback Garmin não concluiu a conexão: {}", resultado);
                    yield redirecionar("error");
                }
            };
        } catch (GarminCanalIndisponivelException e) {
            return redirecionar("error");
        } catch (RuntimeException e) {
            // Só a classe vai ao log: a mensagem poderia carregar o code.
            log.error("Falha inesperada no callback Garmin: {}", e.getClass().getSimpleName());
            return redirecionar("error");
        }
    }

    private ResponseEntity<Void> redirecionar(String status) {
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, base + ROTA_PERFIL_ATLETA + "?" + PARAM_RETORNO + "=" + status)
                .build();
    }
}
