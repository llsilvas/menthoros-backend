package br.com.menthoros.backend.domain.compliance;

/**
 * Versão do JSON Schema de saída do LLM, gravada em {@code tb_llm_call.schema_version}
 * (add-plan-generation-ledger, design D9). Separada de {@link PromptVersion} porque o schema
 * semântico (Fase 4) muda o contrato de saída sem reescrever o prompt inteiro.
 */
public final class SchemaVersion {

    public static final String CURRENT = "schema-v1";

    private SchemaVersion() {
    }
}
