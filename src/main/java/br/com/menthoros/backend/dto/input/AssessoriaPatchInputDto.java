package br.com.menthoros.backend.dto.input;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Atualização parcial da assessoria pelo próprio dono.
 *
 * <p><b>Rejeitar campo desconhecido é o ponto central deste DTO</b>, não um detalhe: sem isso, um
 * {@code corPrimaria} no payload seria descartado em silêncio e o cliente acreditaria ter salvo uma
 * cor que ninguém persistiu — o contrato fantasma que a decisão D3 existe para impedir.
 *
 * <p><b>Por que um {@code @JsonCreator} e não {@code @JsonIgnoreProperties(ignoreUnknown = false)}:</b>
 * a anotação apenas deixa de ignorar, não força a falha — quem decide é
 * {@code FAIL_ON_UNKNOWN_PROPERTIES}, que no Spring Boot vem {@code false} e o projeto não
 * sobrescreve. Verificado por teste: com a anotação sozinha, o payload com {@code corPrimaria}
 * respondia {@code 200}. Ligar a flag globalmente resolveria aqui e mudaria o comportamento de
 * todos os outros endpoints de uma vez, o que esta change não tem escopo para fazer.
 */
@Schema(description = "Campos editáveis da assessoria")
public record AssessoriaPatchInputDto(

        @Schema(description = "Nome da assessoria", example = "Corridas Serra Pro")
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 200, message = "Nome deve ter no máximo 200 caracteres")
        String nome,

        @Schema(description = "Versão lida no GET; se estiver obsoleta a resposta é 409", example = "3")
        @NotNull(message = "Versão é obrigatória para detectar edição concorrente")
        Long version
) {

    /** Único conjunto aceito. Qualquer outra chave no JSON é erro. */
    private static final Set<String> CAMPOS_ACEITOS = Set.of("nome", "version");

    @JsonCreator
    static AssessoriaPatchInputDto doJson(Map<String, Object> campos) {
        Set<String> desconhecidos = new LinkedHashSet<>(campos.keySet());
        desconhecidos.removeAll(CAMPOS_ACEITOS);
        if (!desconhecidos.isEmpty()) {
            throw new IllegalArgumentException(
                    "Campos não editáveis nesta operação: " + desconhecidos);
        }

        return new AssessoriaPatchInputDto(
                texto(campos.get("nome")),
                inteiro(campos.get("version")));
    }

    private static String texto(Object valor) {
        return valor == null ? null : String.valueOf(valor);
    }

    private static Long inteiro(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof Number numero) {
            return numero.longValue();
        }
        throw new IllegalArgumentException("Versão deve ser numérica");
    }
}
