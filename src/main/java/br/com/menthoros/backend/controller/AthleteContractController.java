package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.AthleteContractInputDto;
import br.com.menthoros.backend.dto.input.InvoicePaymentInputDto;
import br.com.menthoros.backend.dto.output.AthleteContractOutputDto;
import br.com.menthoros.backend.dto.output.AthleteInvoiceOutputDto;
import br.com.menthoros.backend.entity.AthleteContract;
import br.com.menthoros.backend.entity.AthleteInvoice;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.mapper.AthleteContractMapper;
import br.com.menthoros.backend.services.AthleteContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Contrato do atleta e mensalidades — só o proprietário da assessoria (e o ADMIN da plataforma,
 * por consistência com os demais endpoints de dono). É o único lugar de onde sai valor (D5).
 *
 * <p>Sem {@code @RequireTenant}, de propósito (design D9): o aspecto devolve 403 e não conhece
 * {@code AthleteInvoice}. O serviço resolve atleta, contrato e mensalidade por {@code (id,
 * tenantId)} e responde 404 para o que não é do tenant — indistinguível de inexistente.</p>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PROPRIETARIO', 'ADMIN')")
@Tag(name = "contrato-atleta", description = "Contrato do atleta com a assessoria e suas mensalidades (proprietário)")
public class AthleteContractController {

    private final AthleteContractService contractService;
    private final AthleteContractMapper mapper;
    private final Clock clock;

    @GetMapping("/atletas/{atletaId}/contrato")
    @Operation(summary = "Contrato ativo do atleta com as mensalidades (mais recente primeiro)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contrato ativo",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AthleteContractOutputDto.class))),
            @ApiResponse(responseCode = "403", description = "Apenas PROPRIETARIO/ADMIN", content = @Content),
            @ApiResponse(responseCode = "404", description = "Atleta sem contrato ativo, inexistente ou de outro tenant", content = @Content)
    })
    public ResponseEntity<AthleteContractOutputDto> getContract(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId) {
        AthleteContract contract = contractService.findActiveContract(atletaId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta sem contrato ativo"));
        return ResponseEntity.ok(toOutput(contract));
    }

    @PutMapping("/atletas/{atletaId}/contrato")
    @Operation(summary = "Cria ou edita o contrato ativo do atleta (upsert)",
            description = "Criar gera a primeira mensalidade. Editar vale só para mensalidades futuras.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contrato criado ou editado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AthleteContractOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "403", description = "Apenas PROPRIETARIO/ADMIN", content = @Content),
            @ApiResponse(responseCode = "404", description = "Atleta inexistente ou de outro tenant", content = @Content),
            @ApiResponse(responseCode = "409", description = "Criação concorrente: repita a requisição", content = @Content)
    })
    public ResponseEntity<AthleteContractOutputDto> upsertContract(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @Valid @RequestBody AthleteContractInputDto input) {
        AthleteContract contract = contractService.createOrUpdate(atletaId, input);
        return ResponseEntity.ok(toOutput(contract));
    }

    @PostMapping("/atletas/{atletaId}/contrato/encerrar")
    @Operation(summary = "Encerra o contrato ativo; mensalidades em aberto ficam como estão")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contrato encerrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AthleteContractOutputDto.class))),
            @ApiResponse(responseCode = "403", description = "Apenas PROPRIETARIO/ADMIN", content = @Content),
            @ApiResponse(responseCode = "404", description = "Atleta sem contrato ativo, inexistente ou de outro tenant", content = @Content)
    })
    public ResponseEntity<AthleteContractOutputDto> endContract(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId) {
        AthleteContract contract = contractService.end(atletaId);
        return ResponseEntity.ok(toOutput(contract));
    }

    @PostMapping("/mensalidades/{invoiceId}/baixa")
    @Operation(summary = "Dá baixa na mensalidade (OPEN → PAID)",
            description = "Data default hoje; valor pago default o valor da mensalidade.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mensalidade paga",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AthleteInvoiceOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "403", description = "Apenas PROPRIETARIO/ADMIN", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mensalidade inexistente ou de outro tenant", content = @Content),
            @ApiResponse(responseCode = "409", description = "Mensalidade não está em aberto", content = @Content)
    })
    public ResponseEntity<AthleteInvoiceOutputDto> markPaid(
            @Parameter(description = "ID da mensalidade") @PathVariable UUID invoiceId,
            @Valid @RequestBody(required = false) InvoicePaymentInputDto input) {
        LocalDate paidAt = input != null ? input.paidAt() : null;
        var paidAmount = input != null ? input.paidAmount() : null;
        AthleteInvoice invoice = contractService.markPaid(invoiceId, paidAt, paidAmount);
        return ResponseEntity.ok(mapper.toOutputDto(invoice, today()));
    }

    @DeleteMapping("/mensalidades/{invoiceId}/baixa")
    @Operation(summary = "Desfaz a baixa (PAID → OPEN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mensalidade de volta a em aberto",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AthleteInvoiceOutputDto.class))),
            @ApiResponse(responseCode = "403", description = "Apenas PROPRIETARIO/ADMIN", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mensalidade inexistente ou de outro tenant", content = @Content),
            @ApiResponse(responseCode = "409", description = "Mensalidade não está paga", content = @Content)
    })
    public ResponseEntity<AthleteInvoiceOutputDto> undoPayment(
            @Parameter(description = "ID da mensalidade") @PathVariable UUID invoiceId) {
        AthleteInvoice invoice = contractService.undoPayment(invoiceId);
        return ResponseEntity.ok(mapper.toOutputDto(invoice, today()));
    }

    @PostMapping("/mensalidades/{invoiceId}/cancelar")
    @Operation(summary = "Cancela a mensalidade (OPEN → CANCELLED): este período não cobra")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mensalidade cancelada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AthleteInvoiceOutputDto.class))),
            @ApiResponse(responseCode = "403", description = "Apenas PROPRIETARIO/ADMIN", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mensalidade inexistente ou de outro tenant", content = @Content),
            @ApiResponse(responseCode = "409", description = "Mensalidade não está em aberto", content = @Content)
    })
    public ResponseEntity<AthleteInvoiceOutputDto> cancel(
            @Parameter(description = "ID da mensalidade") @PathVariable UUID invoiceId) {
        AthleteInvoice invoice = contractService.cancel(invoiceId);
        return ResponseEntity.ok(mapper.toOutputDto(invoice, today()));
    }

    private AthleteContractOutputDto toOutput(AthleteContract contract) {
        return mapper.toOutputDto(contract, contractService.listInvoices(contract.getId()), today());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
