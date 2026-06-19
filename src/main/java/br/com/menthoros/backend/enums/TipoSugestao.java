package br.com.menthoros.backend.enums;

/**
 * Tipo de sugestão gerada pelo job diário para revisão do treinador.
 *
 * <p>Mapeado a partir de {@link MotivoAtencao}:
 * FADIGA/SOBRECARGA/INATIVIDADE → RECOVERY;
 * SEM_PLANO → NEW_PLAN;
 * ADERENCIA/ZONAS_VENCIDAS → PLAN_ADJUST.
 */
public enum TipoSugestao {
    PLAN_ADJUST,
    RECOVERY,
    NEW_PLAN
}
