package br.com.menthoros.backend.dto.eval;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Nota do juiz-LLM com a rubrica completa (plan-generation-eval-set, fatia 2) — só usada em
 * <b>modo candidato</b>, que tem {@code ContextoTreino} completo (histórico, perfil, prova) para
 * julgar progressão e segurança/lesão com sentido. Em modo auditoria (fixtures reais, sem
 * histórico congelado) usa-se {@link NotaJuizReduzida} — nunca este record com campos
 * artificialmente preenchidos.
 *
 * <p>Decisão da task 2.1 (nota do {@code product-reviewer}, GO): rubrica expandida de 4 para 6
 * eixos — os 4 originais (progressão, polarização, especificidade, clareza) não cobriam
 * exequibilidade de carga nem segurança/lesão, notável porque as fixtures já estratificam por
 * "lesão ativa".
 */
public record NotaJuizCompleta(
        @Schema(description = "Progressão de carga coerente com o histórico do atleta") NotaEixo progressao,
        @Schema(description = "Polarização de intensidade (fácil/moderado/forte) adequada") NotaEixo polarizacao,
        @Schema(description = "Especificidade do plano para a prova-alvo do atleta") NotaEixo especificidadeParaProva,
        @Schema(description = "Clareza da comunicação/justificativa do plano") NotaEixo clareza,
        @Schema(description = "Exequibilidade da carga frente à rotina/disponibilidade do atleta")
        NotaEixo exequibilidadeCarga,
        @Schema(description = "Segurança/gestão de risco de lesão do plano") NotaEixo segurancaLesao,
        @Schema(description = "Nota geral, escala 1-5") int notaGeral,
        @Schema(description = "1-3 frases resumindo o veredito geral") String justificativaGeral
) {
}
