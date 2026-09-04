package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Resumo "hoje" do atleta: a data de hoje no fuso dele, o próximo treino planejado, o realizado
 * de hoje (se houver) e as métricas-chave do dia.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resumo do dia para o shell do atleta")
public record AtletaHomeDto(

        @Schema(description = "Hoje no fuso do atleta — o front decide o estado do dia por esta data, nunca pela do aparelho",
                example = "2026-08-27")
        LocalDate hoje,

        @Schema(description = "Próximo treino planejado; omitido quando não há")
        ProximoTreino proximoTreino,

        @Schema(description = "Treino realizado hoje (qualquer origem); omitido quando não há. Com mais de um, o mais recente")
        RealizadoHoje realizadoHoje,

        @Schema(description = "Métricas-chave atuais do atleta")
        MetricasChave metricasChave
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Treino realizado hoje — o que o hero precisa para decidir entre 'Como foi?' e o resumo")
    public record RealizadoHoje(

            @Schema(description = "Id do TreinoRealizado — alvo do POST de feedback")
            UUID id,

            @Schema(description = "Origem do registro", example = "INTERVALS_ICU")
            String fonteDados,

            @Schema(description = "Tipo do treino", example = "FACIL")
            String tipoTreino,

            @Schema(description = "Duração em minutos inteiros; omitida quando ausente", example = "50")
            Integer duracaoMin,

            @Schema(description = "Distância em km; omitida quando ausente", example = "12.5")
            BigDecimal distanciaKm,

            @Schema(description = "RPE 1–10 já gravado (registro manual ou feedback); omitido quando ausente", example = "6")
            Integer percepcaoEsforco,

            @Schema(description = "Sensações do treino, lista fechada; omitida quando ausente", example = "[\"PERNAS_PESADAS\"]")
            List<br.com.menthoros.backend.enums.Sensacao> sensacoes,

            @Schema(description = "Comentário do atleta sobre o treino; ausente quando não escrito")
            String feedbackAtleta,

            @Schema(description = "Carimbo do feedback pós-treino; ausente = 'Como foi?' ainda não respondido")
            LocalDateTime feedbackRegistradoEm
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Próximo treino planejado")
    public record ProximoTreino(

            @Schema(description = "Data do treino", example = "2026-06-18")
            LocalDate data,

            @Schema(description = "Tipo do treino", example = "INTERVALADO")
            String tipoTreino,

            @Schema(description = "Descrição/objetivo do treino")
            String descricao,
            @Schema(description = "Duração planejada em minutos inteiros; omitida quando não prescrita", example = "45")
            Integer duracaoMin,
            @Schema(description = "Zona alvo declarada no treino; omitida quando ausente", example = "Z2")
            String zonaAlvo,
            @Schema(description = "TSS planejado; omitido quando ausente", example = "70")
            Integer tssPlanejado,
            @Schema(description = "IF planejado; omitido quando ausente", example = "0.95")
            Double intensidadePlanejada,
            @Schema(description = "Etapas do treino (mesmo DTO do detalhe do coach); omitidas quando o treino não tem etapas")
            List<EtapaTreinoDto> etapas,

            @Schema(description = "Status de execução do planejado — o hero usa PERDIDO+motivoPulo para o estado 'pulado' (D1)",
                    example = "PENDENTE")
            String statusTreino,

            @Schema(description = "Motivo do pulo, quando o atleta pulou hoje", example = "SEM_TEMPO")
            String motivoPulo
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Métricas-chave do dia (último ponto PMC)")
    public record MetricasChave(

            @Schema(description = "CTL atual (fitness)", example = "52.3")
            Double ctl,

            @Schema(description = "ATL atual (fadiga)", example = "44.0")
            Double atl,

            @Schema(description = "TSB atual (forma)", example = "8.3")
            Double tsb,

            @Schema(description = "TSS do dia", example = "0")
            Integer tss,

            @Schema(description = "Volume do dia (km)", example = "12.5")
            BigDecimal volumeKm,

            @Schema(description = "Faixa de forma (FaixaTsb) resolvida pelo backend a partir do TSB; null quando tsb ausente", example = "FORMA_IDEAL")
            String statusForma
    ) {}
}
