package br.com.menthoros.backend.skills.prescription;

import java.util.List;

/**
 * Payload do resultado da skill de guarda de prescrição de treino.
 *
 * <p>Indica se o plano proposto pelo LLM passou na validação de segurança,
 * com totais calculados e lista de violações detectadas.</p>
 *
 * @param aprovado        true se o plano está dentro dos limites de segurança
 * @param tssTotal        soma do TSS estimado de todas as sessões propostas
 * @param volumeTotalKm   soma do volume em km de todas as sessões propostas
 * @param violacoes       lista de violações encontradas; vazia se {@code aprovado} é true
 */
public record TrainingPrescriptionGuardPayload(
        boolean aprovado,
        double tssTotal,
        double volumeTotalKm,
        List<String> violacoes
) {}
