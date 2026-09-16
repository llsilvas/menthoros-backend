package br.com.menthoros.backend.domain.compliance;

/**
 * Versão do prompt de geração de plano, gravada em {@code tb_llm_call.prompt_version}
 * (add-plan-generation-ledger, design D9). Espelha {@link PlannerVersion}: a constante é o que
 * aparece em query e em conversa; o hash do template ({@code prompt_hash}) pega mudança acidental
 * sem bump. Sobe para {@code plano-v2} no split system/user (system-user-prompt-split).
 */
public final class PromptVersion {

    public static final String CURRENT = "plano-v2";

    private PromptVersion() {
    }
}
