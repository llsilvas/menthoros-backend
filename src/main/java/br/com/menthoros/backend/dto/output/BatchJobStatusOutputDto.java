package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.BatchJobStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Estado atual de um job de geração de planos em lote (retorno do polling).
 *
 * <p>Durante {@code PENDENTE}/{@code EM_PROGRESSO} o progresso é dado apenas
 * pelos contadores ({@code totalAtletas}/{@code gerados}/{@code erros});
 * {@code geradosDetalhes}/{@code errosDetalhes} vêm vazios e só são preenchidos
 * quando o job atinge estado terminal ({@code CONCLUIDO}/{@code CONCLUIDO_COM_ERROS}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Estado de um job de geração de planos em lote")
public record BatchJobStatusOutputDto(

        @Schema(description = "Identificador do job")
        UUID jobId,

        @Schema(description = "Estado atual do job", example = "EM_PROGRESSO")
        BatchJobStatus status,

        @Schema(description = "Total de atletas do lote", example = "10")
        int totalAtletas,

        @Schema(description = "Atletas com plano gerado com sucesso", example = "6")
        int gerados,

        @Schema(description = "Atletas com erro na geração", example = "1")
        int erros,

        @Schema(description = "Detalhe dos planos gerados; vazio até o estado terminal")
        List<BatchGeradoItemDto> geradosDetalhes,

        @Schema(description = "Detalhe dos erros por atleta; vazio até o estado terminal")
        List<BatchErroItemDto> errosDetalhes
) {

    @Schema(description = "Plano gerado com sucesso para um atleta do lote")
    public record BatchGeradoItemDto(
            @Schema(description = "ID do atleta") UUID atletaId,
            @Schema(description = "ID do plano gerado") UUID planoId,
            @Schema(description = "Nome do atleta") String atletaNome
    ) {}

    @Schema(description = "Erro na geração do plano de um atleta do lote")
    public record BatchErroItemDto(
            @Schema(description = "ID do atleta") UUID atletaId,
            @Schema(description = "Motivo do erro (mensagem segura, sem detalhe interno)") String motivo
    ) {}
}
