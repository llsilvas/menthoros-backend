package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.enums.FaixaAtletas;
import br.com.menthoros.backend.enums.PerfilWaitlist;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados de entrada para inscrição na waitlist pública do Menthoros")
@JsonInclude(JsonInclude.Include.NON_NULL)
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

        @Schema(description = "Faixa de atletas atendidos (apenas para treinador)", example = "DE_11_A_30")
        FaixaAtletas qtdAtletas,

        @Schema(description = "Aceite do consentimento LGPD (obrigatório)", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @AssertTrue(message = "O aceite dos termos de uso de dados é obrigatório")
        Boolean aceiteLgpd,

        @Schema(description = "Campo honeypot anti-spam — deve vir vazio", hidden = true)
        String website
) {}
