package br.com.menthoros.backend.enums;

/**
 * Quem transicionou um {@code PlanoSemanal} para {@link PlanoReviewStatus#APROVADO}.
 *
 * <p>Sem esse campo, os dois caminhos de aprovação (revisão manual do coach, ou o auto-approve
 * de Cenário A de confiança — athlete-onboarding-baseline) ficam indistinguíveis assim que
 * persistidos, mesmo publicando o mesmo {@code PlanoAprovadoEvent} (sessão de grilling, 2026-07-21).
 */
public enum OrigemAprovacao {
    COACH,
    AUTO_CONFIANCA_ALTA
}
