package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.MotivoPulo;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Dados de saída de um treino planejado")
@JsonInclude(JsonInclude.Include.NON_NULL)
@lombok.Builder(toBuilder = true)
public record TreinoPlanejadoOutputDto(
        @Schema(description = "Identificador único do treino planejado", example = "123e4567-e89b-12d3-a456-426614174003")
        UUID id,

        @Schema(description = "ID do plano semanal", example = "123e4567-e89b-12d3-a456-426614174001")
        UUID planoSemanalId,

        @Schema(description = "Data do treino", example = "2024-01-15")
        LocalDate dataTreino,

        @Schema(description = "Dia da semana do treino", example = "SEGUNDA")
        DiaSemana diaSemana,

        @Schema(description = "Tipo do treino", example = "TREINO_LONGO")
        TipoTreino tipoTreino,

        @Schema(description = "Descrição do treino", example = "5km aquecimento + 10km ritmo forte + 2km desaquecimento")
        String descricao,

        @Schema(description = "Zona alvo do treino", example = "z2-z3")
        String zonaAlvo,

        @Schema(description = "Duração do treino (formato: HH:MM:SS ou MM:SS)", example = "01:05:30")
        String duracaoMin,

        @Schema(description = "Distância em quilômetros", example = "10.5")
        Double distanciaKm,

        @Schema(description = "Ritmo alvo", example = "5:30 min/km")
        String ritmoAlvo,

        @Schema(description = "Elevação acumulada (ganho) em metros", example = "150")
        Integer elevacaoGanhoMetros,

        @Schema(description = "Elevação perdida (descida) em metros", example = "140")
        Integer elevacaoPerdaMetros,

        @Schema(description = "Observações sobre o treino", example = "Atenção ao ritmo nos primeiros 5km")
        String observacao,

        // ===== MÉTRICAS PLANEJADAS =====

        @Schema(description = "Frequência cardíaca alvo", example = "140-155 bpm")
        String fcAlvo,

        @Schema(description = "Percepção de esforço esperada (1-10)", example = "7")
        Integer percepcaoEsforcoEsperada,

        @Schema(description = "TSS planejado", example = "75")
        Integer tssPlanejado,

        @Schema(description = "Intensidade planejada (IF)", example = "0.85")
        Double intensidadePlanejada,

        @Schema(description = "Justificativa da IA", example = "Treino de base aeróbica para manter volume")
        String justificativaIa,

        @Schema(description = "Indica se o treino foi editado manualmente pelo coach após geração pela IA", example = "false")
        boolean editadoPeloCoach,

        @Schema(description = "Indica se o treino foi adicionado manualmente pelo coach (não gerado pela IA)", example = "false")
        boolean adicionadoPeloCoach,

        // ===== ETAPAS E STATUS =====

        @Schema(description = "Lista de etapas do treino")
        List<EtapaTreinoDto> etapas,

        @Schema(description = "Status de execução do treino", example = "PENDENTE")
        TreinoExecucaoStatus statusTreino,

        // ===== METADADOS =====

        @Schema(description = "Fonte dos dados", example = "IA_GERADO")
        FonteDados fonteDados,

        @Schema(description = "ID externo (referência em sistema de terceiros)", example = "garmin-12345678")
        String externalId,

        // ===== TREINO REALIZADO VINCULADO =====

        @Schema(description = "ID do treino realizado vinculado, se houver")
        UUID treinoRealizadoId,

        @Schema(description = "Percepção de esforço real do atleta (1-10)", example = "8")
        Integer percepcaoEsforcoRealizado,

        // ===== PULO ("Não vou conseguir hoje") =====

        @Schema(description = "Motivo declarado pelo atleta ao pular o treino; só com statusTreino = PERDIDO e pulo explícito",
                example = "SEM_TEMPO")
        MotivoPulo motivoPulo,

        @Schema(description = "Quando o atleta pulou (fuso do atleta); ausente quando o PERDIDO veio do encerramento da semana")
        LocalDateTime puladoEm,

        // ===== ANÁLISE DO ATLETA (analise-ia-treino-atleta) =====

        @Schema(description = "Há análise pós-treino pronta para o atleta neste treino (bloco do atleta disponível e recurso ligado)", example = "false")
        boolean analiseAtletaDisponivel
) {}
