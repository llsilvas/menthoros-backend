package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.enums.CanalIntegracao;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.DispositivoMarca;
import br.com.menthoros.backend.enums.NivelExperiencia;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Rascunho do formulário de onboarding do atleta — todos os campos são "
        + "opcionais neste DTO (CA8, retomar onboarding interrompido); a conclusão (endpoint "
        + "/concluir) é quem exige os dados completos.")
public record AtletaOnboardingInputDto(
        @Schema(description = "Objetivo do atleta com o treinamento", example = "Completar maratona em menos de 4 horas")
        @Size(max = 500, message = "Objetivo deve ter no máximo 500 caracteres")
        String objetivo,

        @Schema(description = "Nível de experiência do atleta em corrida", example = "INTERMEDIARIO")
        NivelExperiencia nivelExperiencia,

        @Schema(description = "Dias da semana disponíveis para treino", example = "[\"SEGUNDA\", \"QUARTA\", \"SEXTA\"]")
        List<DiaSemana> diasDisponiveis,

        @Schema(description = "Volume semanal máximo confortável (km)", example = "40")
        @Positive(message = "Volume semanal máximo deve ser positivo")
        Integer volumeSemanalMax,

        @Schema(description = "Indica se o atleta possui alguma lesão", example = "false")
        Boolean temLesao,

        @Schema(description = "Descrição detalhada da lesão, caso exista")
        @Size(max = 1000, message = "Descrição da lesão deve ter no máximo 1000 caracteres")
        String descricaoLesao,

        @Schema(description = "Data da última lesão", example = "2026-01-15")
        @PastOrPresent(message = "Data da última lesão não pode ser no futuro")
        LocalDate dataUltimaLesao,

        @Schema(description = "Histórico de lesões do atleta")
        @Size(max = 5000, message = "Histórico de lesões deve ter no máximo 5000 caracteres")
        String historicoLesoes,

        @Schema(description = "Maior treino recente do atleta (km)", example = "21.5")
        @Positive(message = "Maior treino recente deve ser positivo")
        BigDecimal maiorTreinoRecenteKm,

        @Schema(description = "Duração disponível por sessão de treino (minutos)", example = "60")
        @Positive(message = "Duração disponível deve ser positiva")
        Integer duracaoDisponivelMin,

        @Schema(description = "Restrições de treino informadas pelo atleta")
        @Size(max = 5000, message = "Restrições deve ter no máximo 5000 caracteres")
        String restricoes,

        @Schema(description = "Modalidade principal do atleta", example = "CORRIDA")
        @Size(max = 30, message = "Modalidade deve ter no máximo 30 caracteres")
        String modalidade,

        @Schema(description = "Percepção subjetiva de condicionamento atual", example = "BOA")
        @Size(max = 30, message = "Percepção de condicionamento deve ter no máximo 30 caracteres")
        String percepcaoCondicionamento,

        @Schema(description = "Canal de integração de treinos (Strava não é oferecido para atletas novos, ADR-0003)", example = "INTERVALS_ICU")
        CanalIntegracao canalIntegracao,

        @Schema(description = "Marca do dispositivo/relógio do atleta", example = "GARMIN")
        DispositivoMarca dispositivoMarca,

        @Schema(description = "Modelo do dispositivo (texto livre, opcional)", example = "Forerunner 265")
        @Size(max = 100, message = "Modelo do dispositivo deve ter no máximo 100 caracteres")
        String dispositivoModelo
) {
}
