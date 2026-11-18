package br.com.menthoros.backend.routing;

/**
 * Classifica a complexidade de uma tarefa de IA para roteamento ao modelo correto.
 *
 * O modelo de cada rota vem de {@code app.llm.routing} no application.yml e os preços de
 * {@code llm-pricing.yml} — não repetir nomes de modelo aqui (a migração de 2026-11 precisou caçá-los).
 * - SIMPLE   → rota simple   (barata, sem reasoning)
 * - STANDARD → rota standard (Anthropic)
 * - COMPLEX  → rota complex  (Anthropic)
 * - EXPERT   → rota expert   (com reasoning)
 * - PLANO    → rota plano    (geração de plano semanal — fluxo mais crítico)
 */
public enum TaskComplexity {

    /** Tradução, extração de dados, tarefas determinísticas de baixo custo. */
    SIMPLE,

    /** Análises simples, detecção de padrões, tarefas de velocidade. */
    STANDARD,

    /** Prescrição de treinos, uso de skills, raciocínio multi-etapa. */
    COMPLEX,

    /** Análise profunda de lesões, raciocínio especialista de longa cadeia. */
    EXPERT,

    /** Geração de plano semanal — modelo com alta capacidade de raciocínio estruturado. */
    PLANO
}
