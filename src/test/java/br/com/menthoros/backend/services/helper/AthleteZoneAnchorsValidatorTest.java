package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.input.AthleteZoneAnchorsInputDto;
import br.com.menthoros.backend.dto.input.AthleteZoneAnchorsInputDto.Fc;
import br.com.menthoros.backend.dto.input.AthleteZoneAnchorsInputDto.Pace;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.exception.InvalidZoneAnchorsException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AthleteZoneAnchorsValidator")
class AthleteZoneAnchorsValidatorTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 3);

    private final AthleteZoneAnchorsValidator validator = new AthleteZoneAnchorsValidator(
            Validation.buildDefaultValidatorFactory().getValidator(),
            new AtletaHojeResolver(Clock.fixed(Instant.parse("2026-11-03T15:00:00Z"), ZoneOffset.UTC)));

    private static AthleteZoneAnchorsInputDto fc(Integer limiar, Integer maxima) {
        return new AthleteZoneAnchorsInputDto(new Fc(limiar, maxima, HOJE, null), null);
    }

    private static AthleteZoneAnchorsInputDto pace(Integer segKm) {
        return new AthleteZoneAnchorsInputDto(null, new Pace(segKm, HOJE, null));
    }

    private static Atleta atleta(Integer fcMaximaGravada) {
        return Atleta.builder().fcMaxima(fcMaximaGravada).build();
    }

    private void rejeitaCom(AthleteZoneAnchorsInputDto input, Atleta atleta, String campo) {
        assertThatThrownBy(() -> validator.validar(input, atleta))
                .isInstanceOfSatisfying(InvalidZoneAnchorsException.class,
                        e -> assertThat(e.getFields()).containsKey(campo));
    }

    @Nested
    @DisplayName("grupos")
    class Grupos {

        @Test
        @DisplayName("corpo sem nenhum grupo é rejeitado")
        void semGrupo() {
            rejeitaCom(new AthleteZoneAnchorsInputDto(null, null), atleta(null), "body");
        }

        @Test
        @DisplayName("só pace ou só fc é aceito")
        void umGrupoBasta() {
            assertThatCode(() -> validator.validar(pace(290), atleta(null))).doesNotThrowAnyException();
            assertThatCode(() -> validator.validar(fc(168, 186), atleta(null))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("fc sem limiar é rejeitado")
        void fcSemLimiar() {
            rejeitaCom(fc(null, 186), atleta(null), "fc.limiar");
        }
    }

    @Nested
    @DisplayName("limites (= CHECKs de V6)")
    class Limites {

        @ParameterizedTest(name = "limiar {0} -> {1}")
        @CsvSource({"99,false", "100,true", "200,true", "201,false"})
        @DisplayName("fc.limiar 100..200")
        void limiarFc(int limiar, boolean aceito) {
            if (aceito) {
                assertThatCode(() -> validator.validar(fc(limiar, null), atleta(null))).doesNotThrowAnyException();
            } else {
                rejeitaCom(fc(limiar, null), atleta(null), "fc.limiar");
            }
        }

        @ParameterizedTest(name = "máxima {0} -> {1}")
        @CsvSource({"119,false", "120,true", "220,true", "221,false"})
        @DisplayName("fc.maxima 120..220")
        void maximaFc(int maxima, boolean aceito) {
            // limiar 100 mantém a regra do topo da Z5 (106) fora do caminho
            if (aceito) {
                assertThatCode(() -> validator.validar(fc(100, maxima), atleta(null))).doesNotThrowAnyException();
            } else {
                rejeitaCom(fc(100, maxima), atleta(null), "fc.maxima");
            }
        }

        @ParameterizedTest(name = "pace {0} -> {1}")
        @CsvSource({"149,false", "150,true", "720,true", "721,false"})
        @DisplayName("pace.limiarSegKm 150..720")
        void paceLimiar(int segKm, boolean aceito) {
            if (aceito) {
                assertThatCode(() -> validator.validar(pace(segKm), atleta(null))).doesNotThrowAnyException();
            } else {
                rejeitaCom(pace(segKm), atleta(null), "pace.limiarSegKm");
            }
        }
    }

    @Nested
    @DisplayName("FC máxima x topo da Z5")
    class MaximaXTopoZ5 {

        @Test
        @DisplayName("limiar 168 com máxima 169 é rejeitada (mínimo 178)")
        void rejeita169() {
            rejeitaCom(fc(168, 169), atleta(null), "fc.maxima");
        }

        @Test
        @DisplayName("limiar 168 com máxima 178 é aceita")
        void aceita178() {
            assertThatCode(() -> validator.validar(fc(168, 178), atleta(null))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("máxima vazia usa a gravada: gravada 169 com limiar 168 é rejeitada")
        void usaGravadaQuandoVazia() {
            rejeitaCom(fc(168, null), atleta(169), "fc.maxima");
        }

        @Test
        @DisplayName("máxima informada vence a gravada")
        void informadaVenceGravada() {
            assertThatCode(() -> validator.validar(fc(168, 180), atleta(169))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("sem nascimento e sem máxima gravada: limiar 185 é aceito (estimada não bloqueia)")
        void estimadaNaoBloqueia() {
            assertThatCode(() -> validator.validar(fc(185, null), atleta(null))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("máxima gravada zero conta como ausente")
        void gravadaZeroEhAusente() {
            assertThatCode(() -> validator.validar(fc(185, null), atleta(0))).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("data do teste")
    class DataDoTeste {

        private AthleteZoneAnchorsInputDto paceEm(LocalDate data) {
            return new AthleteZoneAnchorsInputDto(null, new Pace(290, data, null));
        }

        @Test
        @DisplayName("hoje e 2000-01-01 são aceitos; amanhã e 1999-12-31 não")
        void bordas() {
            assertThatCode(() -> validator.validar(paceEm(HOJE), atleta(null))).doesNotThrowAnyException();
            assertThatCode(() -> validator.validar(paceEm(LocalDate.of(2000, 1, 1)), atleta(null)))
                    .doesNotThrowAnyException();
            rejeitaCom(paceEm(HOJE.plusDays(1)), atleta(null), "pace.dataTeste");
            rejeitaCom(paceEm(LocalDate.of(1999, 12, 31)), atleta(null), "pace.dataTeste");
        }

        @Test
        @DisplayName("'hoje' é o do fuso do atleta: 01:00Z já é dia 4 em UTC, mas ainda dia 3 em Manaus")
        void fusoDoAtleta() {
            AthleteZoneAnchorsValidator emMadrugada = new AthleteZoneAnchorsValidator(
                    Validation.buildDefaultValidatorFactory().getValidator(),
                    new AtletaHojeResolver(Clock.fixed(Instant.parse("2026-11-04T01:00:00Z"), ZoneOffset.UTC)));
            Atleta manaus = Atleta.builder().timezone("America/Manaus").build();

            assertThatCode(() -> emMadrugada.validar(paceEm(LocalDate.of(2026, 11, 3)), manaus))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> emMadrugada.validar(paceEm(LocalDate.of(2026, 11, 4)), manaus))
                    .isInstanceOf(InvalidZoneAnchorsException.class);
        }

        @Test
        @DisplayName("data ausente é rejeitada")
        void ausente() {
            rejeitaCom(paceEm(null), atleta(null), "pace.dataTeste");
        }
    }
}
