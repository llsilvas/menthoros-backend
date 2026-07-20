package br.com.menthoros.backend.domain.compliance;

import java.time.LocalDate;

/**
 * Recorte de uma sessao do plano gerado pelo LLM (pre ou pos-redistribuicao), mapeado na
 * camada de service — o {@code SkeletonComplianceChecker} nunca ve o DTO do LLM nem entidades.
 */
public record GeneratedSessionSnapshot(
        LocalDate data,
        String tipoTreino,
        Integer tssPlanejado,
        String intensidadeZona
) {
}
