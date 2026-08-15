package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.AssessoriaPatchInputDto;
import br.com.menthoros.backend.dto.output.AssessoriaMeOutputDto;
import br.com.menthoros.backend.services.AssessoriaLogoService;
import br.com.menthoros.backend.services.AssessoriaSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Configuração da assessoria pelo próprio coach.
 *
 * <p>Endpoints {@code /me}: o tenant vem do JWT via {@code TenantContext}, nunca do cliente —
 * por isso não usam {@code @RequireTenant}, que valida um parâmetro de recurso que aqui não existe.
 */
@RestController
@RequestMapping("/api/v1/assessorias")
@RequiredArgsConstructor
@Tag(name = "assessoria-settings",
        description = "Configuração da assessoria do usuário autenticado (identidade, plano e uso)")
public class AssessoriaSettingsController {

    private final AssessoriaSettingsService assessoriaSettingsService;
    private final AssessoriaLogoService assessoriaLogoService;

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('TECNICO','PROPRIETARIO','ADMIN')")
    @Operation(summary = "Consultar a assessoria do usuário autenticado",
            description = "Devolve identidade, plano, uso do plano e a versão para concorrência otimista. "
                    + "Consultar não é privilégio de dono — qualquer técnico do tenant enxerga.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Configuração da assessoria",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AssessoriaMeOutputDto.class))),
            @ApiResponse(responseCode = "403", description = "Sem tenant resolvido ou role insuficiente",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Assessoria do tenant não encontrada",
                    content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<AssessoriaMeOutputDto> buscarMinhaAssessoria() {
        return ResponseEntity.ok(assessoriaSettingsService.buscarDoTenantCorrente());
    }

    @PatchMapping("/me")
    @PreAuthorize("hasRole('PROPRIETARIO')")
    @Operation(summary = "Atualizar a assessoria do usuário autenticado",
            description = "Só o dono edita. Exige a versão lida no GET: versão obsoleta devolve 409 "
                    + "em vez de sobrescrever a alteração de outra sessão. Campo fora do contrato "
                    + "(cores, plano, limites, slug) é rejeitado, nunca ignorado.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Assessoria atualizada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AssessoriaMeOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Payload inválido ou campo não editável",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "403", description = "Não é o dono da assessoria",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Assessoria do tenant não encontrada",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "409", description = "Versão obsoleta — recarregue antes de salvar",
                    content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<AssessoriaMeOutputDto> atualizarMinhaAssessoria(
            @Valid @RequestBody AssessoriaPatchInputDto input) {
        return ResponseEntity.ok(assessoriaSettingsService.atualizarDoTenantCorrente(input));
    }

    @PostMapping(value = "/me/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PROPRIETARIO')")
    @Operation(summary = "Enviar ou substituir a logo da assessoria",
            description = "PNG ou JPEG, até 2 MiB e 2048x2048. O conteúdo é decodificado para "
                    + "validação — extensão e Content-Type do cliente não são considerados.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logo armazenada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AssessoriaMeOutputDto.class))),
            @ApiResponse(responseCode = "403", description = "Não é o dono da assessoria",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "409", description = "Versão obsoleta",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "413", description = "Arquivo acima do limite do servidor",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "422", description = "Não é imagem aceita, ou excede tamanho/dimensões",
                    content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<AssessoriaMeOutputDto> enviarLogo(
            @Parameter(description = "Arquivo de imagem (PNG ou JPEG)")
            @RequestPart("arquivo") MultipartFile arquivo,
            @Parameter(description = "Versão lida no GET")
            @RequestParam("version") Long version) throws IOException {
        return ResponseEntity.ok(assessoriaLogoService.substituir(arquivo.getBytes(), version));
    }

    @GetMapping("/me/logo")
    @PreAuthorize("hasAnyRole('TECNICO','PROPRIETARIO','ADMIN')")
    @Operation(summary = "Servir a logo da assessoria",
            description = "Responde 304 quando o If-None-Match bate com o ETag, para não retrafegar "
                    + "a imagem a cada render.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bytes da logo"),
            @ApiResponse(responseCode = "304", description = "Não modificada desde o ETag informado"),
            @ApiResponse(responseCode = "403", description = "Sem tenant resolvido ou role insuficiente"),
            @ApiResponse(responseCode = "404", description = "Assessoria sem logo")
    })
    public ResponseEntity<byte[]> servirLogo(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        String etagAtual = assessoriaLogoService.buscarEtag().orElse(null);
        if (etagAtual == null) {
            return ResponseEntity.notFound().build();
        }

        String etagHttp = "\"" + etagAtual + "\"";
        if (etagHttp.equals(ifNoneMatch)) {
            // Sem corpo: o ponto do 304 é justamente não ler nem trafegar os bytes.
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etagHttp).build();
        }

        return assessoriaLogoService.buscar()
                .map(logo -> ResponseEntity.ok()
                        .eTag("\"" + logo.etag() + "\"")
                        .cacheControl(CacheControl.noCache().cachePrivate())
                        .contentType(MediaType.parseMediaType(logo.contentType()))
                        .body(logo.conteudo()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/me/logo")
    @PreAuthorize("hasRole('PROPRIETARIO')")
    @Operation(summary = "Remover a logo da assessoria",
            description = "Exige a versão, como o PATCH: sem isso uma aba antiga apagaria a logo que "
                    + "outra acabou de enviar, sem conflito e com perda de dado.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Logo removida"),
            @ApiResponse(responseCode = "403", description = "Não é o dono da assessoria"),
            @ApiResponse(responseCode = "409", description = "Versão obsoleta")
    })
    public ResponseEntity<Void> removerLogo(
            @Parameter(description = "Versão lida no GET") @RequestParam("version") Long version) {
        assessoriaLogoService.remover(version);
        return ResponseEntity.noContent().build();
    }
}
