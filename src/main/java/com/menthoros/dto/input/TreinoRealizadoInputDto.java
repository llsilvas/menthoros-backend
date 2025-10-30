package com.menthoros.dto.input;

import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.FonteDados;
import com.menthoros.enums.TipoTreino;
import com.menthoros.enums.TreinoExecucaoStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Dados de entrada para registro de um treino realizado")
public record TreinoRealizadoInputDto(
        @Schema(description = "ID do atleta que realizou o treino", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID atletaId,

        @Schema(description = "ID do plano semanal vinculado", example = "123e4567-e89b-12d3-a456-426614174001")
        UUID planoSemanalId,

        @Schema(description = "ID do treino planejado correspondente", example = "123e4567-e89b-12d3-a456-426614174003")
        UUID treinoPlanejadoId,

        @Schema(description = "Data em que o treino foi realizado", example = "2024-01-15")
        LocalDate dataTreino,

        @Schema(description = "Dia da semana do treino", example = "SEGUNDA")
        DiaSemana diaSemana,

        @Schema(description = "Tipo do treino realizado", example = "TREINO_LONGO")
        TipoTreino tipoTreino,

        @Schema(description = "Descrição detalhada do treino", example = "10km com progressão nos últimos 3km")
        String descricao,

        @Schema(description = "Zona alvo do treino", example = "z2-z3")
        String zonaAlvo,

        @Schema(description = "Duração do treino (formatos aceitos: HH:MM:SS, MM:SS ou apenas minutos)", example = "01:05:30")
        String duracaoMin,

        @Schema(description = "Distância percorrida em quilômetros", example = "10.8")
        Double distanciaKm,

        @Schema(description = "Ritmo alvo planejado", example = "5:30 min/km")
        String ritmoAlvo,

        @Schema(description = "Ritmo médio do treino", example = "5:45 min/km")
        String ritmoMedio,

        @Schema(description = "Elevação acumulada (ganho) em metros", example = "150")
        Integer elevacaoGanhoMetros,

        @Schema(description = "Elevação perdida (descida) em metros", example = "140")
        Integer elevacaoPerdaMetros,

        @Schema(description = "Observações gerais sobre o treino", example = "Condições climáticas favoráveis")
        String observacao,

        // ===== MÉTRICAS FISIOLÓGICAS =====

        @Schema(description = "Frequência cardíaca média durante o treino (bpm)", example = "152")
        Integer fcMedia,

        @Schema(description = "Frequência cardíaca máxima durante o treino (bpm)", example = "178")
        Integer fcMax,

        @Schema(description = "Cadência média durante o treino (passos por minuto)", example = "170")
        Integer cadenciaMedia,

        @Schema(description = "Potência média em watts", example = "245")
        Integer potenciaMedia,

        @Schema(description = "Velocidade média em km/h", example = "12.5")
        Double velocidadeMedia,

        @Schema(description = "Percepção de esforço (escala 1-10)", example = "8", minimum = "1", maximum = "10")
        Integer percepcaoEsforco,

        // ===== FEEDBACK DO ATLETA =====

        @Schema(description = "Comentário ou observação sobre o treino", example = "Treino intenso, últimos 2km foram difíceis")
        String feedbackAtleta,

        @Schema(description = "Qualidade do sono na noite anterior (escala 1-10)", example = "7", minimum = "1", maximum = "10")
        Integer qualidadeSonoNoiteAnterior,

        @Schema(description = "Nível de estresse antes do treino (escala 1-10)", example = "5", minimum = "1", maximum = "10")
        Integer nivelEstresse,

        // ===== METADADOS =====

        @Schema(description = "Fonte dos dados do treino", example = "GARMIN")
        FonteDados fonteDados,

        @Schema(description = "Status de execução do treino", example = "CONCLUIDO")
        TreinoExecucaoStatus status,

        @Schema(description = "ID externo do treino (referência em sistema de terceiros)", example = "garmin-12345678")
        String externalId
) {}
