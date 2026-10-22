package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.FoundersSlotsOutputDto;
import br.com.menthoros.backend.services.FoundersSlotsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fonte única de vagas da turma fundadora — substitui o texto fixo ("10 vagas") duplicado na home
 * e em {@code /waitlist}. Público ({@code /api/v1/founders/slots} em {@code public-paths}), sem
 * tenant. Só números agregados, nunca dado pessoal dos convidados.
 */
@RestController
@RequestMapping("/api/v1/founders")
@RequiredArgsConstructor
@Tag(name = "founders", description = "Vagas da turma fundadora")
public class FoundersSlotsController {

    private final FoundersSlotsService foundersSlotsService;

    @GetMapping("/slots")
    @Operation(summary = "Vagas da turma fundadora",
            description = "Total configurado, ocupadas, restantes e se ainda está aberta. Cache curto (~30s).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vagas calculadas",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = FoundersSlotsOutputDto.class)))
    })
    public ResponseEntity<FoundersSlotsOutputDto> vagas() {
        return ResponseEntity.ok(foundersSlotsService.obterVagas());
    }
}
