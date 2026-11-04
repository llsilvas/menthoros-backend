package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.input.AthleteZonesManualInputDto;
import br.com.menthoros.backend.dto.input.AthleteZonesManualInputDto.Eixo;
import br.com.menthoros.backend.dto.input.AthleteZonesManualInputDto.Faixa;
import br.com.menthoros.backend.enums.ModoZonas;
import br.com.menthoros.backend.exception.InvalidZoneAnchorsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AthleteZoneManualValidator")
class AthleteZoneManualValidatorTest {

    private final AthleteZoneManualValidator validator = new AthleteZoneManualValidator();

    private static List<Faixa> fcValida() {
        return new ArrayList<>(List.of(new Faixa(1, 120, 140), new Faixa(2, 140, 150), new Faixa(3, 150, 160),
                new Faixa(4, 160, 168), new Faixa(5, 168, 178)));
    }

    private static List<Faixa> paceValida() {
        return new ArrayList<>(List.of(new Faixa(1, 345, 375), new Faixa(2, 315, 345), new Faixa(3, 300, 315),
                new Faixa(4, 285, 300), new Faixa(5, 270, 285), new Faixa(6, 246, 270)));
    }

    private static AthleteZonesManualInputDto fc(List<Faixa> faixas) {
        return new AthleteZonesManualInputDto(new Eixo(ModoZonas.MANUAL, faixas), null);
    }

    private static AthleteZonesManualInputDto pace(List<Faixa> faixas) {
        return new AthleteZonesManualInputDto(null, new Eixo(ModoZonas.MANUAL, faixas));
    }

    private void rejeitaCom(AthleteZonesManualInputDto input, String campo) {
        assertThatThrownBy(() -> validator.validar(input))
                .isInstanceOfSatisfying(InvalidZoneAnchorsException.class,
                        e -> assertThat(e.getFields()).containsKey(campo));
    }

    @Nested
    @DisplayName("grupos e modo")
    class Grupos {

        @Test
        @DisplayName("corpo sem grupo é rejeitado")
        void semGrupo() {
            rejeitaCom(new AthleteZonesManualInputDto(null, null), "body");
        }

        @Test
        @DisplayName("modo ausente é rejeitado")
        void semModo() {
            rejeitaCom(new AthleteZonesManualInputDto(new Eixo(null, fcValida()), null), "fc.modo");
        }

        @Test
        @DisplayName("LIMIAR não exige nem valida faixas")
        void limiarIgnoraFaixas() {
            var input = new AthleteZonesManualInputDto(new Eixo(ModoZonas.LIMIAR, List.of(new Faixa(9, 1, 0))), null);
            assertThatCode(() -> validator.validar(input)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("FC e pace válidos passam, incluindo borda compartilhada")
        void validos() {
            assertThatCode(() -> validator.validar(new AthleteZonesManualInputDto(
                    new Eixo(ModoZonas.MANUAL, fcValida()), new Eixo(ModoZonas.MANUAL, paceValida()))))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("zonas do eixo")
    class Zonas {

        @Test
        @DisplayName("MANUAL sem faixas é rejeitado")
        void semFaixas() {
            rejeitaCom(fc(null), "fc.faixas");
        }

        @Test
        @DisplayName("FC com 6 zonas é rejeitada (Z6 não tem FC)")
        void fcComSeisZonas() {
            List<Faixa> faixas = fcValida();
            faixas.add(new Faixa(6, 178, 185));
            rejeitaCom(fc(faixas), "fc.faixas");
        }

        @Test
        @DisplayName("pace com 5 zonas é rejeitado")
        void paceComCincoZonas() {
            List<Faixa> faixas = paceValida();
            faixas.remove(5);
            rejeitaCom(pace(faixas), "pace.faixas");
        }

        @Test
        @DisplayName("zona fora de ordem ou repetida aponta o índice")
        void foraDeOrdem() {
            List<Faixa> faixas = fcValida();
            faixas.set(2, new Faixa(2, 150, 160));
            rejeitaCom(fc(faixas), "fc.faixas[2].zona");
        }

        @Test
        @DisplayName("campo nulo numa faixa aponta o campo")
        void campoNulo() {
            List<Faixa> faixas = fcValida();
            faixas.set(1, new Faixa(2, null, 150));
            rejeitaCom(fc(faixas), "fc.faixas[1].min");
        }
    }

    @Nested
    @DisplayName("faixa")
    class FaixaIndividual {

        @Test
        @DisplayName("De igual a Até é rejeitado")
        void minIgualMax() {
            List<Faixa> faixas = fcValida();
            faixas.set(1, new Faixa(2, 145, 145));
            rejeitaCom(fc(faixas), "fc.faixas[1].max");
        }

        @Test
        @DisplayName("pace com limite mais lento menor que o mais rápido é rejeitado")
        void paceInvertido() {
            List<Faixa> faixas = paceValida();
            faixas.set(2, new Faixa(3, 315, 300));
            rejeitaCom(pace(faixas), "pace.faixas[2].max");
        }

        @Test
        @DisplayName("FC fora de 40–230 é rejeitada")
        void fcForaDosLimites() {
            List<Faixa> faixas = fcValida();
            faixas.set(4, new Faixa(5, 168, 231));
            rejeitaCom(fc(faixas), "fc.faixas[4].max");
        }

        @Test
        @DisplayName("pace fora de 120–900 s/km é rejeitado")
        void paceForaDosLimites() {
            List<Faixa> faixas = paceValida();
            faixas.set(0, new Faixa(1, 345, 901));
            rejeitaCom(pace(faixas), "pace.faixas[0].max");
        }

        @Test
        @DisplayName("limites exatos são aceitos")
        void limitesExatos() {
            List<Faixa> faixas = paceValida();
            faixas.set(0, new Faixa(1, 345, 900));
            faixas.set(5, new Faixa(6, 120, 270));
            assertThatCode(() -> validator.validar(pace(faixas))).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("vizinhas")
    class Vizinhas {

        @Test
        @DisplayName("FC sobreposta aponta o De da zona de cima")
        void fcSobreposta() {
            List<Faixa> faixas = fcValida();
            faixas.set(1, new Faixa(2, 140, 152));
            rejeitaCom(fc(faixas), "fc.faixas[2].min");
        }

        @Test
        @DisplayName("pace sobreposto aponta o limite mais lento da zona de cima")
        void paceSobreposto() {
            List<Faixa> faixas = paceValida();
            faixas.set(2, new Faixa(3, 300, 320));
            rejeitaCom(pace(faixas), "pace.faixas[2].max");
        }

        @Test
        @DisplayName("lacuna entre zonas é aceita")
        void lacunaAceita() {
            List<Faixa> faixas = fcValida();
            faixas.set(2, new Faixa(3, 153, 160));
            assertThatCode(() -> validator.validar(fc(faixas))).doesNotThrowAnyException();
        }
    }
}
