package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.AtletaOnboardingInputDto;
import br.com.menthoros.backend.dto.input.OnboardingConclusaoInputDto;
import br.com.menthoros.backend.dto.output.AtletaOnboardingOutputDto;
import br.com.menthoros.backend.dto.output.CalibracaoStatusOutputDto;
import br.com.menthoros.backend.dto.output.OnboardingConclusaoOutputDto;
import br.com.menthoros.backend.entity.PerfilOnboardingAtleta;
import br.com.menthoros.backend.mapper.OnboardingMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.security.RequireTenant;
import br.com.menthoros.backend.services.onboarding.CalibrationStatusResult;
import br.com.menthoros.backend.services.onboarding.OnboardingConclusionResult;
import br.com.menthoros.backend.services.onboarding.OnboardingDraftInput;
import br.com.menthoros.backend.services.onboarding.OnboardingService;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/atletas/{atletaId}")
@Tag(name = "onboarding", description = "Onboarding do atleta (rascunho, conclusão) e status de calibração "
        + "(athlete-onboarding-baseline). Acesso: o próprio atleta (dono) OU qualquer TECNICO/ADMIN do "
        + "mesmo tenant (coach-como-proxy) — não existe hoje um vínculo de \"técnico responsável\" "
        + "individual por atleta; o controle é por tenant + papel (design.md Decisão 9, CA12).")
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final OnboardingMapper onboardingMapper;

    @PostMapping("/onboarding")
    @PreAuthorize("hasAnyRole('ATLETA','TECNICO','ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Salvar rascunho de onboarding",
            description = "Cria ou atualiza o rascunho (parcial ou completo) — nada é escrito em Atleta "
                    + "neste endpoint (staging em tb_perfil_onboarding_atleta, ADR-0002).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rascunho salvo com sucesso",
                    content = @Content(schema = @Schema(implementation = AtletaOnboardingOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "403", description = "Acesso negado — atleta tentando editar onboarding de outro atleta"),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado")
    })
    public ResponseEntity<AtletaOnboardingOutputDto> salvarRascunho(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @Valid @RequestBody AtletaOnboardingInputDto input) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        boolean chamadorEhCoach = isCoach();
        OnboardingDraftInput draftInput = onboardingMapper.toDraftInput(input, chamadorEhCoach);
        PerfilOnboardingAtleta salvo = onboardingService.salvarRascunho(atletaId, tenantId, draftInput, chamadorEhCoach);
        return ResponseEntity.ok(onboardingMapper.toOutputDto(salvo));
    }

    @GetMapping("/onboarding")
    @PreAuthorize("hasAnyRole('ATLETA','TECNICO','ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Recuperar rascunho de onboarding",
            description = "Retoma um onboarding interrompido (CA8) — lê direto de tb_perfil_onboarding_atleta.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rascunho encontrado",
                    content = @Content(schema = @Schema(implementation = AtletaOnboardingOutputDto.class))),
            @ApiResponse(responseCode = "204", description = "Atleta ainda não iniciou o onboarding"),
            @ApiResponse(responseCode = "403", description = "Acesso negado — atleta tentando ler onboarding de outro atleta")
    })
    public ResponseEntity<AtletaOnboardingOutputDto> buscarRascunho(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Optional<PerfilOnboardingAtleta> perfil = onboardingService.buscarRascunho(atletaId, tenantId, isCoach());
        return perfil.map(p -> ResponseEntity.ok(onboardingMapper.toOutputDto(p)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/onboarding/concluir")
    @PreAuthorize("hasAnyRole('ATLETA','TECNICO','ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Concluir onboarding",
            description = "Migra os campos do rascunho para Atleta, cria/atualiza a Prova alvo a partir de "
                    + "dataProva (CA13) e calcula o baseline/score iniciais, tudo numa única transação. "
                    + "Retorna 409 se Atleta foi editado diretamente após o início do rascunho (conflito).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Onboarding concluído com sucesso",
                    content = @Content(schema = @Schema(implementation = OnboardingConclusaoOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "403", description = "Acesso negado — atleta tentando concluir onboarding de outro atleta"),
            @ApiResponse(responseCode = "404", description = "Atleta ou rascunho não encontrado"),
            @ApiResponse(responseCode = "409", description = "Atleta foi editado diretamente após o início do rascunho")
    })
    public ResponseEntity<OnboardingConclusaoOutputDto> concluirOnboarding(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @Valid @RequestBody OnboardingConclusaoInputDto input) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        OnboardingConclusionResult resultado = onboardingService.concluirOnboarding(
                atletaId, tenantId, isCoach(),
                input.dataProva(), input.tipoProva(), input.distancia(), input.distanciaKm(), input.nomeProva());
        return ResponseEntity.ok(onboardingMapper.toConclusaoOutputDto(resultado));
    }

    @GetMapping("/calibracao")
    @PreAuthorize("hasAnyRole('ATLETA','TECNICO','ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Status de calibração",
            description = "Retorna phase/stage/weekNumber/confidenceScore para o CalibrationBanner do front. "
                    + "Leitura pura — não avalia nem muda o estado de calibração (isso acontece a cada "
                    + "geração de plano).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atleta em calibração",
                    content = @Content(schema = @Schema(implementation = CalibracaoStatusOutputDto.class))),
            @ApiResponse(responseCode = "204", description = "Atleta não está em calibração"),
            @ApiResponse(responseCode = "403", description = "Acesso negado — atleta tentando ler status de outro atleta")
    })
    public ResponseEntity<CalibracaoStatusOutputDto> obterStatusCalibracao(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Optional<CalibrationStatusResult> status = onboardingService.obterStatusCalibracao(atletaId, tenantId, isCoach());
        return status.map(s -> ResponseEntity.ok(onboardingMapper.toCalibracaoStatusOutputDto(s)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Lê a role diretamente do {@link SecurityContextHolder} (ThreadLocal), mesmo padrão de
     * {@code CheckinProntidaoController.isAdmin()} — TECNICO e ADMIN sao coach-como-proxy
     * (design.md Decisao 3), com acesso irrestrito a qualquer atleta do tenant.
     */
    private boolean isCoach() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "ROLE_TECNICO".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
