package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.CanalIntegracao;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.DispositivoMarca;
import br.com.menthoros.backend.enums.NivelExperiencia;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Rascunho ou perfil de onboarding do atleta (tb_perfil_onboarding_atleta)")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AtletaOnboardingOutputDto(
        @Schema(description = "Identificador único do perfil de onboarding")
        UUID id,

        @Schema(description = "Status do onboarding", example = "RASCUNHO")
        String status,

        @Schema(description = "Objetivo do atleta com o treinamento")
        String objetivo,

        @Schema(description = "Nível de experiência do atleta")
        NivelExperiencia nivelExperiencia,

        @Schema(description = "Dias da semana disponíveis para treino")
        List<DiaSemana> diasDisponiveis,

        @Schema(description = "Volume semanal máximo confortável (km)")
        Integer volumeSemanalMax,

        @Schema(description = "Indica se o atleta possui alguma lesão")
        Boolean temLesao,

        @Schema(description = "Descrição detalhada da lesão, caso exista")
        String descricaoLesao,

        @Schema(description = "Data da última lesão")
        LocalDate dataUltimaLesao,

        @Schema(description = "Histórico de lesões do atleta")
        String historicoLesoes,

        @Schema(description = "Maior treino recente do atleta (km)")
        BigDecimal maiorTreinoRecenteKm,

        @Schema(description = "Duração disponível por sessão de treino (minutos)")
        Integer duracaoDisponivelMin,

        @Schema(description = "Restrições de treino informadas pelo atleta")
        String restricoes,

        @Schema(description = "Modalidade principal do atleta")
        String modalidade,

        @Schema(description = "Percepção subjetiva de condicionamento atual")
        String percepcaoCondicionamento,

        @Schema(description = "Indica se o formulário foi preenchido pelo treinador (coach-como-proxy)")
        boolean preenchidoPorCoach,

        @Schema(description = "Canal de integração de treinos")
        CanalIntegracao canalIntegracao,

        @Schema(description = "Marca do dispositivo/relógio do atleta")
        DispositivoMarca dispositivoMarca,

        @Schema(description = "Modelo do dispositivo (texto livre, opcional)")
        String dispositivoModelo,

        @Schema(description = "Data de criação do rascunho")
        Instant criadoEm,

        @Schema(description = "Data da última atualização do rascunho")
        Instant atualizadoEm
) {
}
