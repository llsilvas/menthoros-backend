package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * O que a página de cadastro pré-preenche a partir de um convite ativo. Não expõe validade nem
 * estado — o endpoint responde 404 idêntico para qualquer convite que não esteja ativo.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Dados do inscrito para pré-preencher o cadastro por convite")
public record FoundingInviteLookupOutputDto(
        @Schema(description = "Nome do treinador, como na waitlist", example = "Maria Treinadora") String nome,
        @Schema(description = "E-mail do treinador; o cadastro exige o mesmo", example = "maria@exemplo.com") String email
) {
}
