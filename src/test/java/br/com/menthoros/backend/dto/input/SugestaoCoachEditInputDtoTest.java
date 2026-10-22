package br.com.menthoros.backend.dto.input;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SugestaoCoachEditInputDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("summary de 500 caracteres passa; 501 falha (CA10)")
    void fronteiraDe500() {
        assertThat(validator.validate(new SugestaoCoachEditInputDto("a".repeat(500), 0L))).isEmpty();
        assertThat(validator.validate(new SugestaoCoachEditInputDto("a".repeat(501), 0L))).hasSize(1);
    }

    @Test
    @DisplayName("summary vazio ou só espaços falha (CA7)")
    void summaryEmBranco() {
        assertThat(validator.validate(new SugestaoCoachEditInputDto("", 0L))).isNotEmpty();
        assertThat(validator.validate(new SugestaoCoachEditInputDto("   ", 0L))).isNotEmpty();
    }

    @Test
    @DisplayName("versaoEsperada ausente falha (CA13)")
    void versaoObrigatoria() {
        assertThat(validator.validate(new SugestaoCoachEditInputDto("ok", null))).hasSize(1);
    }
}
