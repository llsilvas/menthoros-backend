package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.fit.FitImportResultado;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.FitUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/atletas")
@Tag(name = "atleta-fit-upload", description = "Importação de treinos a partir de arquivos .fit (Garmin/Wahoo/etc.)")
public class FitUploadController {

    private final FitUploadService fitUploadService;
    private final AtletaProgressService atletaProgressService;

    // @RequireTenant não se aplica: endpoint /me/ resolve o atletaId do JWT via resolverAtletaIdAtual(),
    // sem receber um resource-ID como parâmetro. Isolamento garantido por TenantContext + queries tenant-scoped.

    @PostMapping(value = "/me/treinos/importar-fit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ATLETA','ADMIN')")
    @Operation(summary = "Importar treino a partir de um arquivo .fit")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Treino importado com sucesso (novo registro)",
                    content = @Content(schema = @Schema(implementation = TreinoRealizadoOutputDto.class))),
            @ApiResponse(responseCode = "200", description = "Arquivo já importado anteriormente (mesmo treino retornado, sem duplicar)",
                    content = @Content(schema = @Schema(implementation = TreinoRealizadoOutputDto.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas atletas podem usar este endpoint"),
            @ApiResponse(responseCode = "422", description = "Arquivo inválido, corrompido ou não é um .fit")
    })
    public ResponseEntity<TreinoRealizadoOutputDto> importarFit(
            @RequestBody(description = "Arquivo .fit exportado do dispositivo") @RequestPart("arquivo") MultipartFile arquivo) {
        UUID atletaId = atletaProgressService.resolverAtletaIdAtual();
        FitImportResultado resultado;
        try {
            resultado = fitUploadService.importar(atletaId, arquivo.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo .fit enviado", e);
        }
        HttpStatus status = resultado.novo() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(resultado.treino());
    }
}
