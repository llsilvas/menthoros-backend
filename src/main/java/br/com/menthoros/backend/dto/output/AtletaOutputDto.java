package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.Sexo;
import br.com.menthoros.backend.enums.StatusVencimentoPlano;
import br.com.menthoros.backend.enums.TipoPlanoAtleta;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Schema(description = "Dados de saída de um atleta")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AtletaOutputDto(
        @Schema(description = "Identificador único do atleta", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID id,

        @Schema(description = "Nome completo do atleta", example = "João Silva")
        String nome,

        @Schema(description = "Idade do atleta em anos", example = "30")
        int idade,

        @Schema(description = "Peso do atleta em quilogramas", example = "75.5")
        BigDecimal pesoKg,

        @Schema(description = "Altura do atleta em centímetros", example = "175.0")
        BigDecimal alturaCm,

        @Schema(description = "Objetivo do atleta", example = "Completar maratona em menos de 4 horas")
        String objetivo,

        @Schema(description = "Nível de experiência do atleta", example = "INTERMEDIARIO")
        NivelExperiencia nivelExperiencia,

        @Schema(description = "Dias da semana disponíveis para treino")
        Set<DiaSemana> diasDisponiveis,

        @Schema(description = "Dia preferido para treino longo", example = "SABADO")
        DiaSemana diaPreferidoLongo,

        @Schema(description = "Indica se o atleta possui alguma lesão", example = "false")
        boolean temLesao,

        @Schema(description = "Descrição da lesão, caso exista", example = "Tendinite no joelho direito")
        String descricaoLesao,

        @Schema(description = "Lista de provas do atleta")
        List<ProvaOutputDto> provas,

        @Schema(description = "Tipo de plano do atleta com a assessoria; ausente quando não cadastrado", example = "MENSAL")
        TipoPlanoAtleta tipoPlanoAtleta,

        @Schema(description = "Data de vencimento do plano do atleta com a assessoria; ausente quando não cadastrado", example = "2026-08-15")
        LocalDate dataVencimentoPlano,

        @Schema(description = "Status de vencimento derivado (EM_DIA/PROXIMO_VENCIMENTO/VENCIDO); ausente quando dataVencimentoPlano não cadastrada", example = "PROXIMO_VENCIMENTO")
        StatusVencimentoPlano statusVencimentoPlano,

        @Schema(description = "E-mail do atleta; ausente em atletas cadastrados antes do campo existir", example = "joao@exemplo.com")
        String email,

        @Schema(description = "Sexo do atleta; ausente quando não cadastrado", example = "MASCULINO")
        Sexo sexo) {
}
