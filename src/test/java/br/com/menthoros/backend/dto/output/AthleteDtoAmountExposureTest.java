package br.com.menthoros.backend.dto.output;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Design D5: os DTOs de atleta que todo treinador lê nunca carregam valor de contrato ou de
 * mensalidade. Campos financeiros existem só em {@link AthleteContractOutputDto} e
 * {@link AthleteInvoiceOutputDto}, atrás de PROPRIETARIO. A prova em HTTP está em
 * {@code AthleteBillingExposureTest}.
 */
class AthleteDtoAmountExposureTest {

    private static final List<String> FINANCEIRO = List.of("amount", "valor", "paid", "pago");

    @ParameterizedTest
    @ValueSource(classes = {AtletaOutputDto.class, CoachAtletaResumoDto.class, AtletaPerfilCoachOutputDto.class})
    @DisplayName("nenhum componente do record (nem dos aninhados) tem nome financeiro")
    void semCampoFinanceiro(Class<?> dto) {
        assertThat(nomesFinanceiros(dto)).isEmpty();
    }

    private static List<String> nomesFinanceiros(Class<?> tipo) {
        if (!tipo.isRecord()) {
            return List.of();
        }
        return Arrays.stream(tipo.getRecordComponents())
                .flatMap(c -> {
                    String nome = c.getName().toLowerCase(Locale.ROOT);
                    var proprio = FINANCEIRO.stream().anyMatch(nome::contains)
                            ? java.util.stream.Stream.of(tipo.getSimpleName() + "." + c.getName())
                            : java.util.stream.Stream.<String>empty();
                    return java.util.stream.Stream.concat(proprio, nomesFinanceiros(tipoDoComponente(c)).stream());
                })
                .toList();
    }

    /** Desce em records aninhados declarados no próprio DTO (e nos elementos de List<...>). */
    private static Class<?> tipoDoComponente(RecordComponent c) {
        if (c.getGenericType() instanceof java.lang.reflect.ParameterizedType pt
                && pt.getActualTypeArguments()[0] instanceof Class<?> elemento) {
            return elemento;
        }
        return c.getType();
    }
}
