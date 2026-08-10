package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.CoachSignupInputDto;
import br.com.menthoros.backend.dto.output.CoachSignupOutputDto;
import br.com.menthoros.backend.config.signup.CoachSignupProperties;
import br.com.menthoros.backend.services.CoachSignupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Auto-cadastro público de assessoria.
 *
 * <p>Fica sob {@code /api/public/**} — namespace tenant-less por definição, isento no
 * {@code JwtTenantFilter}. Sem essa isenção, um {@code Authorization} residual de outra sessão
 * (o frontend injeta o header globalmente) chegaria sem {@code tenant_id} e derrubaria o cadastro
 * com 403.</p>
 *
 * <p>Sem {@code @RequireTenant} e sem {@code @PreAuthorize}: a rota roda <strong>antes</strong> de
 * existir tenant ou usuário — é ela que os cria.</p>
 */
@RestController
@RequestMapping("/api/public/coach-signups")
@RequiredArgsConstructor
@Tag(name = "coach-signup", description = "Auto-cadastro público de assessoria e do seu coach")
public class CoachSignupController {

    private static final String MDC_CORRELATION_ID = "correlationId";

    private final CoachSignupService coachSignupService;
    private final CoachSignupProperties properties;

    @Operation(summary = "Cadastra uma assessoria e seu coach",
            description = "Não devolve token. A autenticação subsequente é feita pelo fluxo "
                    + "Authorization Code + PKCE contra o Keycloak, iniciado pelo frontend.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Assessoria e coach provisionados; e-mail de verificação enviado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "409", description = "Identificador ou e-mail já em uso, ou chave de idempotência reusada com outro conteúdo"),
            @ApiResponse(responseCode = "429", description = "Limite de cadastros excedido"),
            @ApiResponse(responseCode = "413", description = "Corpo acima do limite"),
            @ApiResponse(responseCode = "404", description = "Auto-cadastro desligado por feature flag"),
            @ApiResponse(responseCode = "502", description = "Falha na integração com o Keycloak")
    })
    @PostMapping
    public ResponseEntity<CoachSignupOutputDto> cadastrar(
            @Valid @RequestBody CoachSignupInputDto dto,
            @Parameter(description = "Chave de idempotência; sem ela, o duplo clique criaria duas assessorias")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        /*
         * Flag desligada responde 404, não 503.
         *
         * 503 diria "existe e volta logo" — anuncia a um scanner um endpoint público de
         * provisionamento que ainda não foi lançado, e convida a retry que nunca vai funcionar.
         * 404 é o que um recurso inexistente responde, que é o que ele é enquanto não for ligado.
         *
         * Se um dia a flag for usada como kill switch de algo JÁ lançado, 503 passa a ser a resposta
         * mais honesta — aí é uma linha.
         */
        if (!properties.isEnabled()) {
            return ResponseEntity.notFound().build();
        }

        // Gerada quando ausente para que o caminho seja sempre o mesmo. Chave gerada aqui não
        // protege do duplo clique — só o cliente sabe que dois envios são a mesma intenção — mas
        // mantém o rastro completo em vez de deixar a operação sem registro.
        String chave = (idempotencyKey == null || idempotencyKey.isBlank())
                ? UUID.randomUUID().toString()
                : idempotencyKey;

        String correlationId = UUID.randomUUID().toString();
        MDC.put(MDC_CORRELATION_ID, correlationId);
        try {
            CoachSignupOutputDto saida = coachSignupService.cadastrar(dto, chave, correlationId);
            return ResponseEntity.status(HttpStatus.CREATED).body(saida);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }
}
