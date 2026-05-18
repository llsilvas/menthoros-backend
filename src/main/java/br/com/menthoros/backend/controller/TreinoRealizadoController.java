package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.TreinoRealizadoInputDto;
import br.com.menthoros.backend.dto.output.ResumoSemanalTreinoDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.services.StravaActivityService;
import br.com.menthoros.backend.services.TreinoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.ResponseEntity.noContent;

import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.http.ResponseEntity.*;

@Tag(name = "Treinos Realizados", description = "Operações relacionadas ao registro de treinos realizados")
@RestController
@Validated
@RequestMapping("/api/v1/treinos")
public class TreinoRealizadoController {

    private final TreinoService treinoService;
    private final TreinoMapper treinoMapper;
    private final StravaActivityService stravaActivityService;

    public TreinoRealizadoController(TreinoService treinoService, TreinoMapper treinoMapper,
                                     StravaActivityService stravaActivityService) {
        this.treinoService = treinoService;
        this.treinoMapper = treinoMapper;
        this.stravaActivityService = stravaActivityService;
    }

    @Operation(summary = "Marcar treino como realizado",
               description = "Registra a execução de um treino planejado com os dados reais da atividade")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Treino registrado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TreinoRealizadoOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos fornecidos",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Treino planejado não encontrado",
                    content = @Content(mediaType = "application/json"))
    })
    @PostMapping("{treinoPlanejadoId}/marcar-realizado")
    public ResponseEntity<TreinoRealizadoOutputDto> criarTreino(
            @Parameter(description = "ID do treino planejado que foi realizado")
            @PathVariable("treinoPlanejadoId") UUID treinoPlanejadoId,
            @Valid @RequestBody TreinoRealizadoInputDto treinoRealizadoInputDto) {
        TreinoRealizado treinoRealizado = treinoService.addTreino(treinoPlanejadoId, treinoRealizadoInputDto);
        return status(HttpStatus.CREATED).body(treinoMapper.toOutputDto(treinoRealizado));
    }

    @Operation(summary = "Marcar treino como perdido",
               description = "Registra que um treino planejado não foi realizado")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Treino marcado como perdido com sucesso"),
            @ApiResponse(responseCode = "400", description = "Treino já realizado não pode ser marcado como perdido",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Treino planejado não encontrado",
                    content = @Content(mediaType = "application/json"))
    })
    @PatchMapping("{treinoPlanejadoId}/marcar-perdido")
    public ResponseEntity<Void> marcarPerdido(
            @Parameter(description = "ID do treino planejado")
            @PathVariable("treinoPlanejadoId") UUID treinoPlanejadoId) {
        treinoService.marcarTreinoPerdido(treinoPlanejadoId);
        return noContent().build();
    }

    @PostMapping("{atletaId}/lancar-treino")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Lançar treinos manualmente para o atleta",
               description = "Lança um treino manualmente para o atleta, registrando os dados da execução")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Treino lançado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TreinoRealizadoOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos fornecidos",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "403", description = "Acesso negado - apenas TECNICO e ADMIN podem lançar treinos",
                    content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<TreinoRealizadoOutputDto> lancarTreino(
            @Parameter(description = "ID do atleta que está realizando o treino")
            @PathVariable("atletaId") UUID atletaId,
            @Valid @RequestBody TreinoRealizadoInputDto treinoRealizadoInputDto) {
        TreinoRealizadoOutputDto treinoRealizadoOutputDto = treinoService.lancarTreino(atletaId, treinoRealizadoInputDto);
        return status(HttpStatus.CREATED).body(treinoRealizadoOutputDto);
    }

    @Operation(summary = "Atualizar treino realizado",
               description = "Atualiza campos observacionais e de feedback de um treino já registrado. " +
                             "Campos estruturais (atleta, data, tipo) são imutáveis. " +
                             "Quando percepcaoEsforco for informado, dispara análise AI assíncrona.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Treino atualizado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TreinoRealizadoOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "403", description = "Não autenticado ou sem permissão",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Treino não encontrado ou não pertence ao tenant",
                    content = @Content(mediaType = "application/json"))
    })
    @PutMapping("/realizados/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TreinoRealizadoOutputDto> updateTreino(
            @Parameter(description = "ID do treino realizado a ser atualizado")
            @PathVariable UUID id,
            @Valid @RequestBody TreinoRealizadoInputDto dto) {
        return ok(treinoService.updateTreino(id, dto));
    }

    @Operation(summary = "Enriquecer treino com detalhes do Strava",
               description = "Busca o detalhe completo da atividade na API do Strava e enriquece o treino realizado. "
                           + "Se o Garmin/Strava tiver registrado o perceived_exertion, preenche o RPE automaticamente "
                           + "e dispara análise AI assíncrona.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Treino enriquecido com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TreinoRealizadoOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Treino não é proveniente do Strava",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "403", description = "Não autenticado ou sem permissão",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Treino não encontrado",
                    content = @Content(mediaType = "application/json"))
    })
    @PostMapping("/realizados/{id}/enriquecer-strava")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TreinoRealizadoOutputDto> enriquecerComStrava(
            @Parameter(description = "ID do treino realizado")
            @PathVariable UUID id) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        return ok(stravaActivityService.enriquecerTreinoComStrava(id, tenantId));
    }

    @Operation(summary = "Obter resumo semanal de treinos",
               description = "Retorna um resumo agregado dos treinos realizados em uma semana, incluindo volume total, TSS, duração e breakdown por dia")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Resumo semanal obtido com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResumoSemanalTreinoDto.class))),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado",
                    content = @Content(mediaType = "application/json"))
    })
    @GetMapping("/realizados/resumo-semana")
    public ResponseEntity<ResumoSemanalTreinoDto> getResumoSemanal(
            @Parameter(description = "ID do atleta", required = true)
            @RequestParam UUID atletaId,
            @Parameter(description = "Data para calcular a semana (padrão: data atual)")
            @RequestParam(required = false) LocalDate semana) {
        ResumoSemanalTreinoDto resumo = treinoService.getResumoSemanal(atletaId, semana);
        return ResponseEntity.ok(resumo);
    }
}
