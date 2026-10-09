package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.enums.FaixaAtletas;
import br.com.menthoros.backend.enums.PerfilWaitlist;
import br.com.menthoros.backend.enums.WatchBrand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados de entrada para inscrição na waitlist pública do Menthoros")
public record WaitlistInputDto(

        @Schema(description = "Nome do interessado", example = "Maria Treinadora", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 120)
        String nome,

        @Schema(description = "E-mail de contato", example = "maria@exemplo.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Email
        @Size(max = 180)
        String email,

        @Schema(description = "Telefone/WhatsApp (opcional)", example = "+55 11 99999-9999")
        @Size(max = 20)
        String telefone,

        @Schema(description = "Perfil do interessado", example = "TREINADOR", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        PerfilWaitlist perfil,

        @Schema(description = "Faixa de atletas atendidos (apenas para treinador/proprietário)", example = "DE_11_A_30")
        FaixaAtletas qtdAtletas,

        @Schema(description = "Marca de relógio predominante dos atletas (opcional, só relevante para treinador/proprietário)", example = "GARMIN")
        WatchBrand watchBrand,

        @Schema(description = "Aceite do consentimento LGPD (obrigatório)", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @AssertTrue(message = "O aceite dos termos de uso de dados é obrigatório")
        Boolean aceiteLgpd,

        @Schema(description = "Campo honeypot anti-spam — deve vir vazio", hidden = true)
        String website,

        @Schema(description = "Parâmetro utm_source da URL de origem (ex.: instagram)", example = "instagram")
        @Size(max = 255)
        String utmSource,

        @Schema(description = "Parâmetro utm_medium da URL de origem (ex.: social)", example = "social")
        @Size(max = 255)
        String utmMedium,

        @Schema(description = "Parâmetro utm_campaign da URL de origem (ex.: turma-fundadora)", example = "turma-fundadora")
        @Size(max = 255)
        String utmCampaign,

        @Schema(description = "Parâmetro utm_content da URL de origem", example = "bio-link")
        @Size(max = 255)
        String utmContent,

        @Schema(description = "Caminho da página de origem da inscrição (ex.: /waitlist)", example = "/waitlist")
        @Size(max = 255)
        // Restrito a caminho relativo same-site: sem isso, campo livre persistido por um endpoint
        // anônimo é risco de stored-XSS latente se algum painel futuro renderizar sem escapar
        // (security review de expand-waitlist-access-contract).
        @Pattern(regexp = "^/[\\w\\-/]*$", message = "landingPath deve ser um caminho relativo (ex.: /waitlist)")
        String landingPath,

        @Schema(description = "Referrer HTTP no momento da inscrição, quando disponível")
        @Size(max = 255)
        String referrer
) {}
