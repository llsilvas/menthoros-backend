package br.com.menthoros.backend.services.onboarding;

import java.util.List;
import java.util.UUID;

/**
 * Deduplica atividades entre fontes para o calculo do baseline (design.md
 * Decisao 2, athlete-onboarding-baseline).
 *
 * <p>Roda como leitura dentro do {@code OnboardingService} — NAO no momento
 * da ingestao. Recebe o historico ja normalizado (produzido por
 * {@link ActivityNormalizer} a partir de {@code TreinoRealizado} ja
 * persistidos por qualquer fonte) e devolve uma lista deduplicada. Nenhum
 * {@code TreinoRealizado} e criado, alterado ou apagado por este servico —
 * atividades descartadas no merge sao registradas em auditoria
 * (append-only), nunca removidas do historico real.
 */
public interface ActivityDedupService {

    /**
     * Idempotente: NAO — cada chamada grava novos registros de auditoria para
     * as duplicatas encontradas (nao ha dedup do proprio registro de
     * auditoria); chamar 2x com o mesmo historico duplica linhas de
     * auditoria (mesma classe de residual aceito em design.md Decisao 2 —
     * 2 calculos de baseline concorrentes do mesmo atleta).
     * Efeitos colaterais: escrita em {@code tb_atividade_proveniencia_descartada}.
     * Tenant-aware: SIM — {@code tenantId} explicito, usado na auditoria.
     */
    List<NormalizedActivity> deduplicar(List<NormalizedActivity> historico, UUID tenantId);
}
