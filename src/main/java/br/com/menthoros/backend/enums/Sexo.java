package br.com.menthoros.backend.enums;

/**
 * Sexo biológico do atleta — usado nas fórmulas de FC máxima, VO2max e zonas. Os valores são os
 * mesmos que o front envia ({@code Sexo} em {@code types/Atleta.ts}); persistido pelo nome (V85).
 */
public enum Sexo { MASCULINO, FEMININO, OUTRO }
