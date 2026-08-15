package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.services.CoachOnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conclusão do wizard de boas-vindas do coach.
 *
 * <p><b>A tag Swagger é `coach-onboarding`, não `onboarding`, e isso não é preferência.</b> Já
 * existe {@code OnboardingController} com a tag {@code onboarding}, servindo
 * {@code /api/v1/atletas/{id}/onboarding/concluir} — o onboarding <i>do atleta</i>, outro conceito.
 * O gerador de client do frontend (`openapi-typescript-codegen`) deriva o nome da classe de serviço
 * da tag, então reaproveitar o nome produziria dois {@code OnboardingService} conflitantes no
 * TypeScript.
 *
 * <p>Sem {@code @RequireTenant}: a anotação valida um parâmetro de ID de recurso, e este endpoint é
 * self-resolving (resolve o chamador pelo {@code sub} do JWT). O isolamento vem do
 * {@code JwtTenantFilter} + consulta filtrada por {@code sub} e tenant no serviço.
 */
@RestController
@RequestMapping("/api/v1/users/me/onboarding")
@RequiredArgsConstructor
@Tag(name = "coach-onboarding",
        description = "Wizard de boas-vindas do treinador (distinto do onboarding do atleta)")
public class CoachOnboardingController {

    private final CoachOnboardingService coachOnboardingService;

    @PostMapping("/concluir")
    @PreAuthorize("hasAnyRole('TECNICO','PROPRIETARIO','ADMIN')")
    @Operation(summary = "Marcar o wizard de boas-vindas como concluído",
            description = "Idempotente: concluir de novo devolve 204 sem efeito. O usuário é "
                    + "resolvido pelo JWT — não recebe id nem tenant do cliente.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Onboarding concluído (ou já estava)"),
            @ApiResponse(responseCode = "401", description = "Requisição sem JWT válido",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem tenant resolvido ou role insuficiente",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado no tenant atual",
                    content = @Content)
    })
    public ResponseEntity<Void> concluir() {
        coachOnboardingService.concluir();
        return ResponseEntity.noContent().build();
    }
}
