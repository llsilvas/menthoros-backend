package br.com.menthoros.backend.skills.core;

import java.util.List;

/**
 * Resultado estruturado produzido por uma skill de domínio.
 *
 * <p>Contém o payload tipado da análise, metadados de qualidade (severity e confidence),
 * evidências que embasam o resultado e recomendações de ação para o coach/LLM.</p>
 *
 * @param <T>             tipo do payload gerado pela skill
 * @param skillKey        identificador único da skill (ex: "recovery-load-v1")
 * @param skillVersion    versão da skill (ex: "1.0")
 * @param severity        severidade do resultado (INFO, WARNING, CRITICAL, BLOCKER)
 * @param confidence      confiança no resultado baseada na qualidade dos dados (LOW, MEDIUM, HIGH)
 * @param payload         resultado tipado da análise
 * @param evidence        fatos objetivos que embasam o resultado (para rastreabilidade)
 * @param recommendations ações sugeridas ao coach ou injetadas no prompt do LLM
 */
public record SkillResult<T>(
        String skillKey,
        String skillVersion,
        SkillSeverity severity,
        SkillConfidence confidence,
        T payload,
        List<String> evidence,
        List<String> recommendations
) {}
