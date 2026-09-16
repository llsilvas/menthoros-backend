package br.com.menthoros.backend.domain.compliance;

/**
 * Versão do JSON Schema de saída do LLM, gravada em {@code tb_llm_call.schema_version}
 * (add-plan-generation-ledger, design D9). Separada de {@link PromptVersion} porque o schema
 * semântico (Fase 4) muda o contrato de saída sem reescrever o prompt inteiro.
 *
 * <p>{@link #CURRENT} continua sendo o valor do caminho v1 (default de todo tenant fora da
 * allowlist, {@code semantic-session-schema}) — {@link #V2} é o valor novo, resolvido pelo caller
 * (não uma troca de {@code CURRENT}, que quebraria o caminho v1).</p>
 */
public final class SchemaVersion {

    public static final String CURRENT = "schema-v1";
    public static final String V2 = "schema-v2";

    private SchemaVersion() {
    }
}
