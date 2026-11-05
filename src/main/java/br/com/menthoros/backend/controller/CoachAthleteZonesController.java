package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.AthleteZoneAnchorsInputDto;
import br.com.menthoros.backend.dto.input.AthleteZonesManualInputDto;
import br.com.menthoros.backend.dto.output.AthleteTrainingZonesOutputDto;
import br.com.menthoros.backend.services.CoachAthleteZonesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Zonas de pace e FC de um atleta para o treinador. Restrito a {@code TECNICO}/{@code ADMIN}. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/coach/atletas")
@PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
@Tag(name = "coach-athlete-zones", description = "Zonas de treino do atleta para o treinador")
public class CoachAthleteZonesController {

    private final CoachAthleteZonesService coachAthleteZonesService;

    @GetMapping("/{atletaId}/zonas")
    // Sem @RequireTenant de propósito: o aspecto responde 403 para atleta de outro tenant, e o 404 do
    // serviço (findByIdAndTenantId) não revela que o atleta existe em outra assessoria (CA4).
    @Operation(
            summary = "Zonas de treino do atleta",
            description = "Seis zonas com faixa de pace e FC, as âncoras (limiares e datas dos testes), "
                    + "o ajuste de pace por fadiga de hoje (informativo, fora das faixas) e a "
                    + "disponibilidade de cada eixo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Zonas calculadas",
                    content = @Content(schema = @Schema(implementation = AthleteTrainingZonesOutputDto.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão"),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado no tenant")
    })
    public ResponseEntity<AthleteTrainingZonesOutputDto> buscarZonas(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId) {
        return ResponseEntity.ok(coachAthleteZonesService.buscarZonas(atletaId));
    }

    @PutMapping("/{atletaId}/zonas/ancoras")
    // Sem @RequireTenant, pelo mesmo motivo do GET: atleta de outro tenant é 404, não 403.
    @Operation(
            summary = "Cadastra os limiares que geram as zonas do atleta",
            description = "Grava as âncoras de FC (limiar, máxima, data do teste, origem) e/ou de pace "
                    + "(limiar em s/km, data do teste, origem). Grupo ausente mantém aquele eixo; FC máxima "
                    + "vazia mantém a vigente. Devolve as zonas recalculadas, como o GET.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Âncoras gravadas; zonas recalculadas",
                    content = @Content(schema = @Schema(implementation = AthleteTrainingZonesOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Âncora inválida (campo e motivo em 'fields')"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão"),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado no tenant")
    })
    // Sem @Valid de propósito: com problemdetails ligado, o advice padrão do Spring responde ao
    // MethodArgumentNotValidException antes do GlobalExceptionHandler e some com o caminho do campo. A
    // validação (Bean Validation + regras cruzadas) é feita pelo AthleteZoneAnchorsValidator no serviço.
    public ResponseEntity<AthleteTrainingZonesOutputDto> salvarAncoras(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @RequestBody AthleteZoneAnchorsInputDto input) {
        return ResponseEntity.ok(coachAthleteZonesService.salvarAncoras(atletaId, input));
    }

    @PostMapping("/{atletaId}/zonas/simulacao")
    @Operation(
            summary = "Simula as zonas que as âncoras gerariam",
            description = "Mesmo corpo e mesma resposta do PUT de âncoras, sem gravar nada. POST porque o "
                    + "corpo é estruturado e o limiar fisiológico não deve ir em query string nem em log de acesso.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Zonas simuladas; nada foi gravado",
                    content = @Content(schema = @Schema(implementation = AthleteTrainingZonesOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Âncora inválida (campo e motivo em 'fields')"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão"),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado no tenant")
    })
    public ResponseEntity<AthleteTrainingZonesOutputDto> simular(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @RequestBody AthleteZoneAnchorsInputDto input) {
        return ResponseEntity.ok(coachAthleteZonesService.simular(atletaId, input));
    }

    @PutMapping("/{atletaId}/zonas/manuais")
    // Sem @RequireTenant e sem @Valid, pelos mesmos motivos do PUT de âncoras: 404 para outro tenant e o
    // caminho do campo ("fc.faixas[2].min") preservado pelo validador do serviço.
    @Operation(
            summary = "Define o modo das zonas por eixo e as faixas manuais",
            description = "Por eixo (fc, pace): LIMIAR volta ao cálculo pelo limiar e apaga as faixas manuais; "
                    + "MANUAL grava as faixas De/Até (FC Z1–Z5 em bpm, pace Z1–Z6 em s/km), que passam a valer "
                    + "para o prompt da IA, o push ao relógio e o editor de etapas. Grupo ausente mantém o eixo. "
                    + "Devolve as zonas como o GET.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Modo gravado; zonas efetivas devolvidas",
                    content = @Content(schema = @Schema(implementation = AthleteTrainingZonesOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Faixa inválida (campo e motivo em 'fields', ex.: fc.faixas[2].min)"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão"),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado no tenant")
    })
    public ResponseEntity<AthleteTrainingZonesOutputDto> salvarZonasManuais(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @RequestBody AthleteZonesManualInputDto input) {
        return ResponseEntity.ok(coachAthleteZonesService.salvarZonasManuais(atletaId, input));
    }
}
