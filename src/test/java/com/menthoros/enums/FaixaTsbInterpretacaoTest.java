package com.menthoros.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FaixaTsbInterpretacaoTest {

    @Test
    /**
     * Documenta o ISSUE-03:
     * a interpretação de FADIGA_ALTA não pode ser igual à de FADIGA_EXCESSIVA,
     * senão o sistema não comunica diferença entre TSB crítico e sobrecarga.
     */
    void deveDiferenciarInterpretacaoEntreFadigaExcessivaEFadigaAlta() {
        assertEquals("Fadiga excessiva", FaixaTsb.classificar(-40.0).getInterpretacao());
        assertEquals("Fadiga alta", FaixaTsb.classificar(-32.0).getInterpretacao());
    }
}

