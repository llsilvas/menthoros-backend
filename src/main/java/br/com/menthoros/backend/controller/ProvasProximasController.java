package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.ProvaProximaDto;
import br.com.menthoros.backend.dto.output.ProvasProximasResponseDto;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.repository.ProvaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/provas")
@Tag(name = "Provas Próximas", description = "Operações para listagem de provas próximas de todos os atletas")
public class ProvasProximasController {

    private final ProvaRepository provaRepository;

    public ProvasProximasController(ProvaRepository provaRepository) {
        this.provaRepository = provaRepository;
    }

    @GetMapping("/proximas")
    @Operation(summary = "Listar provas próximas de todos os atletas",
               description = "Retorna todas as provas de todos os atletas nos próximos 15 dias, ordenadas pela data mais próxima")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de provas próximas retornada com sucesso",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProvasProximasResponseDto.class))),
        @ApiResponse(responseCode = "500", description = "Erro ao buscar provas",
                content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ProvasProximasResponseDto> getProvasProximas() {
        List<Prova> provas = provaRepository.findUpcomingProvasNext15Days();

        List<ProvaProximaDto> dtoList = provas.stream()
            .map(p -> {
                LocalDate dataProva = p.getDataProva();
                long diasFaltando = ChronoUnit.DAYS.between(LocalDate.now(), dataProva);

                return new ProvaProximaDto(
                    p.getId(),
                    p.getAtleta().getId(),
                    p.getAtleta().getNome(),
                    p.getNomeProva(),
                    p.getDataProva().toString(),
                    p.getTipoProva().toString(),
                    p.getDistancia().toString(),
                    p.getDistanciaKm() != null ? p.getDistanciaKm().doubleValue() : null,
                    p.getTempoObjetivo() != null ? p.getTempoObjetivo().toString() : null,
                    p.getStatusProva().toString(),
                    Math.toIntExact(diasFaltando)
                );
            })
            .collect(Collectors.toList());

        ProvasProximasResponseDto response = new ProvasProximasResponseDto(
            dtoList,
            dtoList.size(),
            LocalDateTime.now().toString()
        );

        return ResponseEntity.ok(response);
    }
}
