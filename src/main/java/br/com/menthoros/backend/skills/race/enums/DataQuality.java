package br.com.menthoros.backend.skills.race.enums;

/** Qualidade dos dados de histórico de treino disponíveis para a skill. */
public enum DataQuality {
    /** 8+ semanas com FC e pace consistentes. */
    FULL,
    /** 4–7 semanas ou dados parcialmente sem FC. */
    PARTIAL,
    /** Menos de 4 semanas ou ausência de FC em todos os treinos. */
    SPARSE
}
