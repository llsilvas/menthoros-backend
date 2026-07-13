package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;


@Schema(description = "Dados de saída de um treino realizado")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TreinoRealizadoOutputDto(
        @Schema(description = "Identificador único do treino realizado", example = "123e4567-e89b-12d3-a456-426614174004")
        UUID id,

        @Schema(description = "Data do treino", example = "2024-01-15")
        LocalDate dataTreino,

        @Schema(description = "Dia da semana", example = "SEGUNDA")
        DiaSemana diaSemana,

        @Schema(description = "Tipo do treino", example = "TREINO_LONGO")
        TipoTreino tipoTreino,

        @Schema(description = "Descrição detalhada do treino", example = "10km com progressão nos últimos 3km")
        String descricao,

        @Schema(description = "Zona alvo do treino", example = "z2-z3")
        String zonaAlvo,

        @Schema(description = "Duração do treino (formato: HH:MM:SS ou MM:SS)", example = "01:05:30")
        String duracaoMin,

        @Schema(description = "Distância em quilômetros", example = "10.8")
        Double distanciaKm,

        @Schema(description = "Ritmo alvo planejado", example = "5:30 min/km")
        String ritmoAlvo,

        @Schema(description = "Ritmo médio realizado", example = "5:45 min/km")
        String paceMedia,

        @Schema(description = "Elevação acumulada (ganho) em metros", example = "150")
        Integer elevacaoGanhoMetros,

        @Schema(description = "Elevação perdida (descida) em metros", example = "140")
        Integer elevacaoPerdaMetros,

        @Schema(description = "Observações gerais sobre o treino", example = "Condições climáticas favoráveis")
        String observacao,

        // ===== MÉTRICAS FISIOLÓGICAS =====

        @Schema(description = "Frequência cardíaca média (bpm)", example = "152")
        Integer fcMedia,

        @Schema(description = "Frequência cardíaca máxima (bpm)", example = "178")
        Integer fcMax,

        @Schema(description = "Cadência média em passos por minuto", example = "170")
        Integer cadenciaMedia,

        @Schema(description = "Potência média em watts", example = "245")
        Integer potenciaMedia,

        @Schema(description = "Velocidade média em km/h", example = "12.5")
        Double velocidadeMedia,

        @Schema(description = "Percepção de esforço (1-10)", example = "8")
        Integer percepcaoEsforco,

        @Schema(description = "TSS calculado do treino", example = "85")
        Integer tssCalculado,

        @Schema(description = "Método usado para calcular TSS", example = "FC")
        String metodoCalculoTss,

        @Schema(description = "Intensidade real (IF)", example = "0.85")
        Double intensidadeReal,

        @Schema(description = "Decoupling aeróbico Pa:HR (% de queda de eficiência da 1ª p/ 2ª metade); "
                        + "null quando não aplicável (esforço não contínuo ou dados insuficientes). "
                        + "Campo legado — o envelope 'decoupling' traz o mesmo valor com proveniência",
                example = "4.2")
        Double decouplingPercentual,

        @Schema(description = "Envelope do decoupling com Pw:HR, proveniência e motivos de null por métrica")
        DecouplingResultadoDto decoupling,

        // ===== FEEDBACK DO ATLETA =====

        @Schema(description = "Comentário sobre o treino", example = "Treino intenso, últimos 2km foram difíceis")
        String feedbackAtleta,

        @Schema(description = "Qualidade do sono na noite anterior (1-10)", example = "7")
        Integer qualidadeSonoNoiteAnterior,

        @Schema(description = "Nível de estresse antes do treino (1-10)", example = "5")
        Integer nivelEstresse,

        // ===== METADADOS =====

        @Schema(description = "Fonte dos dados", example = "GARMIN")
        FonteDados fonteDados,

        @Schema(description = "Status do treino", example = "CONCLUIDO")
        TreinoExecucaoStatus status,

        @Schema(description = "ID externo (referência em sistema de terceiros)", example = "garmin-12345678")
        String externalId,

        // ===== ETAPAS REALIZADAS =====

        @Schema(description = "Etapas detalhadas do treino realizado")
        List<EtapaRealizadaOutputDto> etapasRealizadas,

        // ===== ANÁLISE ESTRUTURAL =====

        @Schema(description = "Sugestão de reclassificação do tipo de treino gerada por análise estrutural das etapas (opcional, apenas quando houver divergência detectada)")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        SugestaoReclassificacaoDto sugestaoReclassificacao
) {}
