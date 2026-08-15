package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.PlanoAssessoria;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Configuração da assessoria do principal autenticado.
 *
 * <p>Distinto de {@link AssessoriaOutputDto}, que serve ao cadastro administrativo: aqui não há
 * {@code keycloakOrganizationId} nem {@code dominio} editável, e o uso do plano vem calculado.
 *
 * <p>As cores da assessoria <b>não</b> aparecem aqui de propósito: nesta change elas não são
 * editáveis nem consumidas por nenhum cliente, e expor campo que ninguém altera cria contrato morto.
 */
@Schema(description = "Configuração da assessoria do usuário autenticado")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssessoriaMeOutputDto(

        @Schema(description = "Identificador único da assessoria")
        UUID id,

        @Schema(description = "Nome da assessoria", example = "Corridas Serra")
        String nome,

        @Schema(description = "Indica se há logo cadastrada", example = "true")
        boolean temLogo,

        @Schema(description = "Rota do próprio produto que serve a logo; null quando não há",
                example = "/api/v1/assessorias/me/logo")
        String logoUrl,

        @Schema(description = "Plano contratado", example = "BASIC")
        PlanoAssessoria plano,

        @Schema(description = "Uso atual do plano")
        Uso uso,

        @Schema(description = "Versão para concorrência otimista; ecoar no PATCH", example = "3")
        Long version
) {

    @Schema(description = "Consumo do plano — somente leitura")
    public record Uso(

            @Schema(description = "Atletas ativos", example = "7")
            long atletas,

            @Schema(description = "Limite de atletas do plano", example = "10")
            Integer maxAtletas,

            @Schema(description = "Técnicos ativos, incluindo o dono", example = "1")
            long tecnicos,

            @Schema(description = "Limite de técnicos do plano", example = "1")
            Integer maxTecnicos
    ) {}
}
