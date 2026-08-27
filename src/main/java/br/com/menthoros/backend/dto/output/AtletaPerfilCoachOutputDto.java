package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.ConfiancaInferencia;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.FonteLimiarInferencia;
import br.com.menthoros.backend.enums.MotivoAtencao;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.Sensacao;
import br.com.menthoros.backend.enums.Severidade;
import br.com.menthoros.backend.enums.StatusSugestao;
import br.com.menthoros.backend.enums.StatusVencimentoPlano;
import br.com.menthoros.backend.enums.TipoPlanoAtleta;
import br.com.menthoros.backend.enums.TipoSugestao;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Perfil consolidado de um atleta para o coach (endpoint único agregador)")
public record AtletaPerfilCoachOutputDto(

        @Schema(description = "ID do atleta")
        UUID atletaId,

        @Schema(description = "Nome completo do atleta", example = "Ana Silva")
        String nomeAtleta,

        @Schema(description = "Objetivo declarado pelo atleta", example = "Correr maratona em menos de 4 horas")
        String objetivo,

        @Schema(description = "Prova alvo do atleta; null se não configurada")
        ProvaOutputDto proximaProva,

        @Schema(description = "Nível de experiência; null se não configurado", example = "INTERMEDIARIO")
        String nivelExperiencia,

        @Schema(description = "Idade do atleta; null se não configurada", example = "32")
        Integer idade,

        @Schema(description = "Série PMC dos últimos 90 dias")
        List<PmcPontoDto> pmc,

        @Schema(description = "Aderência semanal das últimas 8 semanas; lista vazia se sem dados de treino")
        List<AderenciasSemanalDto> aderenciaSemanal,

        @Schema(description = "Plano mais recente do atleta com semanaFim >= hoje; null se não existe")
        PlanoVigenteDto planoVigente,

        @Schema(description = "Sinais de atenção recentes do atleta (top 3)")
        List<SinalRecenteDto> sinaisRecentes,

        @Schema(description = "Sugestões de IA recentes do atleta (top 3)")
        List<SugestaoRecenteDto> sugestoesRecentes,

        @Schema(description = "Recordes pessoais do atleta")
        List<RecordeDto> recordes,

        @Schema(description = "Momento de geração deste perfil")
        Instant geradoEm,

        @Schema(description = "Campos que não carregaram por erro interno; null quando todos ok")
        List<String> avisos,

        @Schema(description = "Limiares fisiológicos inferidos por dados recentes; null quando limiares estão atualizados ou sem dados suficientes")
        LimiareisInferidosDto limiareisInferidos,

        @Schema(description = "Tipo de plano do atleta com a assessoria; ausente quando não cadastrado", example = "MENSAL")
        TipoPlanoAtleta tipoPlanoAtleta,

        @Schema(description = "Data de vencimento do plano do atleta com a assessoria; ausente quando não cadastrado", example = "2026-08-15")
        LocalDate dataVencimentoPlano,

        @Schema(description = "Status de vencimento derivado (EM_DIA/PROXIMO_VENCIMENTO/VENCIDO); ausente quando dataVencimentoPlano não cadastrada", example = "PROXIMO_VENCIMENTO")
        StatusVencimentoPlano statusVencimentoPlano,

        @Schema(description = "Treinos realizados dos últimos 7 dias, mais recente primeiro — inclui feedback pós-treino (RPE/sensações/comentário) quando registrado")
        List<RealizadoRecenteDto> realizadosRecentes
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Plano semanal mais recente com semanaFim >= hoje")
    public record PlanoVigenteDto(

            @Schema(description = "ID do plano")
            UUID planoId,

            @Schema(description = "Data de início da semana do plano")
            LocalDate semanaInicio,

            @Schema(description = "Data de fim da semana do plano")
            LocalDate semanaFim,

            @Schema(description = "Status de revisão do plano")
            PlanoReviewStatus reviewStatus,

            @Schema(description = "Treinos planejados da semana; lista vazia quando reviewStatus != APROVADO")
            List<TreinoPlanejadoResumoDto> treinos
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Treino realizado recente, com feedback do atleta quando registrado (athlete-training-loop, D3)")
    public record RealizadoRecenteDto(

            @Schema(description = "ID do treino realizado")
            UUID id,

            @Schema(description = "Data do treino")
            LocalDate dataTreino,

            @Schema(description = "Tipo do treino", example = "INTERVALADO")
            String tipoTreino,

            @Schema(description = "Origem do registro", example = "INTERVALS_ICU")
            FonteDados fonteDados,

            @Schema(description = "Duração em minutos inteiros; ausente quando não disponível", example = "50")
            Integer duracaoMin,

            @Schema(description = "Distância em km; ausente quando não disponível", example = "12.5")
            Double distanciaKm,

            @Schema(description = "Percepção de esforço (1-10); ausente até o atleta registrar", example = "6")
            Integer percepcaoEsforco,

            @Schema(description = "Sensações do treino; ausente quando não registradas")
            List<Sensacao> sensacoes,

            @Schema(description = "Comentário do atleta sobre o treino")
            String feedbackAtleta,

            @Schema(description = "Quando o feedback foi registrado; ausente = ainda não respondido")
            LocalDateTime feedbackRegistradoEm
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Resumo compacto de um treino planejado")
    public record TreinoPlanejadoResumoDto(

            @Schema(description = "ID do treino planejado")
            UUID id,

            @Schema(description = "Dia da semana", example = "SEGUNDA")
            String diaSemana,

            @Schema(description = "Tipo de treino", example = "CORRIDA_LEVE")
            String tipoTreino,

            @Schema(description = "Distância planejada em km", example = "10.5")
            double distanciaKm,

            @Schema(description = "Status de execução: PENDENTE, CONCLUIDO, REALIZADO, PERDIDO, PARCIAL, LIVRE",
                    example = "PENDENTE")
            String statusExecucao,

            @Schema(description = "Duração planejada em formato ISO-8601", example = "PT60M")
            String duracaoMin,

            @Schema(description = "Zona alvo do treino", example = "Z2")
            String zonaAlvo,

            @Schema(description = "Percepção de esforço esperada (1–10)", example = "6")
            Integer percepcaoEsforcoEsperada,

            @Schema(description = "Etapas do treino (aquecimento, esforço, recuperação, desaquecimento)")
            List<EtapaTreinoDto> etapas,

            @Schema(description = "Status de sincronização com canal externo (intervals.icu)", example = "SINCRONIZADO")
            String statusSincronizacao,

            @Schema(description = "Atleta tem conexão intervals.icu ativa")
            Boolean atletaConectadoIntervalsIcu
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Limiares fisiológicos inferidos por dados recentes dos últimos 30 dias")
    public record LimiareisInferidosDto(

            @Schema(description = "FC limiar estimado em bpm", example = "163")
            Integer fcLimiarEstimado,

            @Schema(description = "Pace limiar estimado formatado (mm:ss/km)", example = "4:45/km")
            String paceLimiarEstimadoFormatado,

            @Schema(description = "Confiança da inferência de FC")
            ConfiancaInferencia confiancaInferenciaFc,

            @Schema(description = "Confiança da inferência de pace")
            ConfiancaInferencia confiancaInferenciaPace,

            @Schema(description = "Fonte do paceLimiarEstimado: PROVA_REGISTRADA (derivado de uma prova real "
                    + "recente e válida) ou MEDIA_TREINOS (mediana do quintil mais rápido dos treinos); "
                    + "null se paceLimiarEstimado nunca foi calculado por esta lógica")
            FonteLimiarInferencia fonteLimiarEstimado,

            @Schema(description = "Data em que a inferência foi executada")
            LocalDate dataInferenciaLimiar
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Sinal de atenção recente para este atleta")
    public record SinalRecenteDto(

            @Schema(description = "Motivo da atenção", example = "FADIGA")
            MotivoAtencao motivo,

            @Schema(description = "Severidade do sinal", example = "ALTA")
            Severidade severidade,

            @Schema(description = "Momento de geração do sinal")
            Instant geradoEm,

            @Schema(description = "Ação sugerida ao treinador")
            String acaoSugerida,

            @Schema(description = "ID da sugestão associada; null se não há sugestão vinculada")
            UUID sugestaoId
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Sugestão de IA recente para este atleta")
    public record SugestaoRecenteDto(

            @Schema(description = "ID da sugestão")
            UUID id,

            @Schema(description = "Tipo da sugestão", example = "RECOVERY")
            TipoSugestao tipo,

            @Schema(description = "Status no ciclo de revisão", example = "PENDING")
            StatusSugestao status,

            @Schema(description = "Momento de criação da sugestão")
            Instant criadoEm
    ) {}
}
