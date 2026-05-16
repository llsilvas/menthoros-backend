package br.com.menthoros.backend.routing;

/**
 * Classifica a complexidade de uma tarefa de IA para roteamento ao modelo correto.
 *
 * Mapeamento de custo (estimado por operação):
 * - SIMPLE   → GPT-4o-mini    (~R$ 0,0008)
 * - STANDARD → Claude Haiku   (~R$ 0,025)
 * - COMPLEX  → Claude Sonnet  (~R$ 0,10)
 * - EXPERT   → GPT-4o         (~R$ 0,08)
 */
public enum TaskComplexity {

    /** Tradução, extração de dados, tarefas determinísticas de baixo custo. */
    SIMPLE,

    /** Análises simples, detecção de padrões, tarefas de velocidade. */
    STANDARD,

    /** Prescrição de treinos, uso de skills, raciocínio multi-etapa. */
    COMPLEX,

    /** Análise profunda de lesões, raciocínio especialista de longa cadeia. */
    EXPERT
}
