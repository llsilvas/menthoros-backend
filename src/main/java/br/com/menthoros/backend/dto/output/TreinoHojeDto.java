package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * O treino planejado de hoje para o modo treino do atleta, com o alvo de cada etapa já
 * resolvido pelo backend — a mesma cadeia que o push ao intervals.icu usa. O front não deriva
 * bpm de zona nem decide entre FC e pace.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Treino planejado de hoje com alvos resolvidos por etapa (modo treino)")
public record TreinoHojeDto(

        @Schema(description = "Hoje no fuso do atleta", example = "2026-08-27")
        LocalDate hoje,

        @Schema(description = "Id do TreinoPlanejado")
        UUID id,

        @Schema(description = "Tipo do treino", example = "INTERVALADO")
        String tipoTreino,

        @Schema(description = "Descrição/objetivo do treino")
        String descricao,

        @Schema(description = "Duração planejada em minutos inteiros; omitida quando não prescrita", example = "45")
        Integer duracaoMin,

        @Schema(description = "Zona alvo declarada no treino; omitida quando ausente", example = "Z4")
        String zonaAlvo,

        @Schema(description = "TSS planejado; omitido quando ausente", example = "70")
        Integer tssPlanejado,

        @Schema(description = "Status de execução do planejado", example = "PENDENTE")
        String statusTreino,

        @Schema(description = "Motivo do pulo, quando o atleta pulou hoje", example = "SEM_TEMPO")
        String motivoPulo,

        @Schema(description = "Quando o atleta pulou (fuso do atleta)")
        LocalDateTime puladoEm,

        @Schema(description = "Etapas na ordem de execução; omitidas quando o treino não tem etapas")
        List<EtapaAlvoDto> etapas
) {

    /** Qual grandeza o relógio vai controlar nesta etapa — a mesma precedência do push. */
    public enum AlvoPrimario { FC, PACE, NENHUM }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Etapa com o alvo efetivo: FC vence pace; pace sem FC assume; sem dado confiável, NENHUM")
    public record EtapaAlvoDto(

            Integer ordem,
            String tipoEtapa,
            String descricao,
            @Schema(description = "Duração da etapa em minutos; omitida quando a etapa é por distância")
            Integer duracaoMin,
            @Schema(description = "Distância da etapa em km; omitida quando a etapa é por tempo")
            Double distanciaKm,
            @Schema(description = "Id do bloco de repetição ao qual a etapa pertence; omitido fora de bloco")
            UUID blocoId,
            @Schema(description = "Repetições do bloco; omitido fora de bloco")
            Integer blocoRepeticoes,

            AlvoPrimario alvoPrimario,
            @Schema(description = "FC alvo mínima em bpm; só quando alvoPrimario = FC", example = "145")
            Integer fcAlvoMin,
            @Schema(description = "FC alvo máxima em bpm; só quando alvoPrimario = FC", example = "151")
            Integer fcAlvoMax,
            @Schema(description = "Pace alvo m:ss ou m:ss-m:ss; só quando alvoPrimario = PACE", example = "4:30-4:45")
            String paceAlvo,
            @Schema(description = "Prescrição rebaixada a informação: o pace quando a FC venceu, ou a FC que não pôde ser resolvida")
            String textoSecundario
    ) {}
}
