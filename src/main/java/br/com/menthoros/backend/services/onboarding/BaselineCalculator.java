package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.enums.NivelExperiencia;

import java.util.List;
import java.util.UUID;

/**
 * Calcula o baseline (CTL/ATL/TSB) do atleta no onboarding (design.md
 * Decisao 3/6, athlete-onboarding-baseline).
 *
 * <p>Reusa {@code TsbService} para o calculo real de CTL/ATL/TSB. Tres
 * cenarios, modelados como uma unica formula continua (nao 3 branches
 * separados): a proporcao de peso da heuristica varia linearmente de 0
 * (>= 8 semanas observadas, Cenario A — baseline direto) a 1 (0 semanas,
 * Cenario C — 100% heuristica), com o meio-termo (Cenario B) sendo o blend
 * proporcional — mesmo padrao de interpolacao linear ja usado pelo
 * Confidence Scorer (design.md Decisao 3, criterio "Historico &gt;= 8
 * semanas").
 */
public interface BaselineCalculator {

    /**
     * Idempotente: SIM — mesmo historico e nivelExperiencia sempre produzem
     * o mesmo resultado (nao persiste nada, so calcula).
     * Efeitos colaterais: chama {@code TsbService.recalcularHistoricoCompleto},
     * que persiste {@code MetricasDiarias} (efeito colateral do colaborador,
     * nao deste metodo).
     * Tenant-aware: NAO diretamente — o {@code atletaId} ja resolve o
     * escopo via os repositorios/servicos chamados internamente.
     */
    BaselineResult calcular(UUID atletaId, NivelExperiencia nivelExperiencia, List<NormalizedActivity> historicoDeduplicado);
}
