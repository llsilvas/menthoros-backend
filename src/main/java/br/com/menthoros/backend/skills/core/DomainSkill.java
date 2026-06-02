package br.com.menthoros.backend.skills.core;

/**
 * Interface genérica para todas as skills de domínio do Menthoros.
 *
 * <p>Uma skill é um componente determinístico de análise fisiológica ou de elegibilidade
 * que recebe um input tipado e produz um {@link SkillResult} estruturado.
 * Skills são descobertas automaticamente pelo {@link SkillRegistry} e executadas
 * em ordem pelo {@link SkillOrchestratorService}.</p>
 *
 * <p>Regras de implementação:</p>
 * <ul>
 *   <li>Skills NÃO devem receber entidades JPA — o serviço chamador é responsável
 *       por mapear as entidades para os tipos de input da skill.</li>
 *   <li>Skills devem ser stateless e thread-safe.</li>
 *   <li>Exceptions internas devem ser capturadas pelo orquestrador, não propagadas.</li>
 * </ul>
 *
 * @param <I> tipo do input da skill
 * @param <O> tipo do payload no {@link SkillResult} retornado
 */
public interface DomainSkill<I, O> {

    /**
     * Identificador único da skill no sistema.
     * Convenção: {@code <dominio>-<função>-v<versão-maior>} (ex: "recovery-load-v1").
     *
     * @return chave única imutável desta skill
     */
    String skillKey();

    /**
     * Versão semântica desta implementação da skill.
     * Permite múltiplas versões coexistindo no registry.
     *
     * @return versão como string (ex: "1.0", "2.1")
     */
    String skillVersion();

    /**
     * Categoria funcional desta skill.
     * Usada para filtragem e agrupamento no SkillRegistry.
     *
     * @return categoria da skill
     */
    SkillCategory category();

    /**
     * Executa a análise da skill com o input e contexto fornecidos.
     *
     * <p><b>Idempotente:</b> dependente da implementação — documentar na subclasse.</p>
     * <p><b>Side Effects:</b> NONE por padrão — documentar se houver na subclasse.</p>
     * <p><b>Tenant-aware:</b> SIM — o tenantId está disponível via {@code context.tenantId()}.</p>
     *
     * @param input   dados de entrada tipados para a análise
     * @param context contexto de execução com atletaId, tenantId e dataReferencia
     * @return resultado estruturado com payload, evidências e recomendações
     */
    SkillResult<O> execute(I input, SkillContext context);
}
