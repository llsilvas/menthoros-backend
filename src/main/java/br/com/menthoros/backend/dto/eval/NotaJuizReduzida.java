package br.com.menthoros.backend.dto.eval;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Nota do juiz-LLM com a rubrica reduzida (plan-generation-eval-set, fatia 2) — usada em
 * <b>modo auditoria</b>: fixtures reais do ledger não carregam histórico de treino, então
 * "progressão" (exige comparar com semanas anteriores) e "segurança/lesão" (exige o quadro clínico
 * completo do atleta) não são julgáveis com sentido a partir só da resposta congelada — ficam fora
 * daqui, nunca preenchidas com adivinhação (achado da rodada 3 de DoR, Codex).
 */
public record NotaJuizReduzida(
        @Schema(description = "Polarização de intensidade (fácil/moderado/forte) adequada") NotaEixo polarizacao,
        @Schema(description = "Especificidade do plano para a prova-alvo do atleta") NotaEixo especificidadeParaProva,
        @Schema(description = "Clareza da comunicação/justificativa do plano") NotaEixo clareza,
        @Schema(description = "Nota geral, escala 1-5, só sobre os eixos observáveis") int notaGeral,
        @Schema(description = "1-3 frases resumindo o veredito geral") String justificativaGeral
) {
}
