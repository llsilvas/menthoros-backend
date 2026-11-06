package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.enums.AlvoPrimario;
import br.com.menthoros.backend.dto.input.EtapaInputDto;
import br.com.menthoros.backend.entity.EtapaTreino;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O PATCH de treino limpa as etapas e as reconstrói a partir do {@link EtapaInputDto}. Campo que o
 * DTO não carrega nasce nulo — foi assim que a edição apagou o {@code blocoId} (corrigido em
 * {@code preservar-serie-estruturada-na-edicao}) e depois o {@code ritmoAlvo}, que o planner
 * prescreve por etapa. Estes testes prendem os campos que a edição precisa preservar.
 */
class EtapaMapperTest {

    private final EtapaMapper mapper = new EtapaMapperImpl();

    @Nested
    @DisplayName("toEntity(EtapaInputDto)")
    class ToEntity {

        @Test
        @DisplayName("carrega a meta de intensidade da etapa: FC e ritmo")
        void carregaMetaDeIntensidade() {
            EtapaTreino etapa = mapper.toEntity(etapaInput("140-150 bpm", "5:00-5:15/km"));

            assertThat(etapa.getFcAlvoEtapa()).isEqualTo("140-150 bpm");
            assertThat(etapa.getRitmoAlvo()).isEqualTo("5:00-5:15/km");
        }

        @Test
        @DisplayName("etapa prescrita só por ritmo não ganha FC inventada")
        void apenasRitmo() {
            EtapaTreino etapa = mapper.toEntity(etapaInput(null, "4:00-4:10/km"));

            assertThat(etapa.getRitmoAlvo()).isEqualTo("4:00-4:10/km");
            assertThat(etapa.getFcAlvoEtapa()).isNull();
        }

        @Test
        @DisplayName("etapa prescrita só por FC não ganha ritmo inventado")
        void apenasFc() {
            EtapaTreino etapa = mapper.toEntity(etapaInput("140-150 bpm", null));

            assertThat(etapa.getFcAlvoEtapa()).isEqualTo("140-150 bpm");
            assertThat(etapa.getRitmoAlvo()).isNull();
        }

        @Test
        @DisplayName("tipoEtapa é normalizado para maiúsculas")
        void normalizaTipo() {
            EtapaTreino etapa = mapper.toEntity(new EtapaInputDto(
                    "principal", "Tiro", 5, null, null, null, 1, null, null));

            assertThat(etapa.getTipoEtapa()).isEqualTo("PRINCIPAL");
        }

        @Test
        @DisplayName("alvoPrimario do input chega à entidade; ausente continua nulo (legado)")
        void carregaAlvoPrimario() {
            EtapaInputDto comEixo = new EtapaInputDto("PRINCIPAL", "Tiro", 5, null, null, "5:00-5:15/km", 1, null,
                    null, AlvoPrimario.PACE);

            assertThat(mapper.toEntity(comEixo).getAlvoPrimario()).isEqualTo(AlvoPrimario.PACE);
            assertThat(mapper.toEntity(etapaInput(null, "5:00-5:15/km")).getAlvoPrimario()).isNull();
        }

        @Test
        @DisplayName("blocoId nunca vem do cliente — segue ignorado de propósito")
        void blocoIdIgnorado() {
            EtapaTreino etapa = mapper.toEntity(etapaInput("140-150 bpm", "5:00-5:15/km"));

            assertThat(etapa.getBlocoId()).isNull();
        }

        @Test
        @DisplayName("duracaoSeg e a fonte da verdade: duracaoMin e derivado e o minuto enviado e ignorado")
        void carregaDuracaoSeg() {
            EtapaTreino etapa = mapper.toEntity(new EtapaInputDto(
                    "INTERVALADO", "Tiro", 7, null, null, "4:10/km", 1, null, null, null, 250));
            assertThat(etapa.getDuracaoSeg()).isEqualTo(250);
            assertThat(etapa.getDuracaoMin()).isEqualTo(4);
        }

        @ParameterizedTest(name = "{0} s -> duracaoMin {1}")
        @CsvSource({"10, 0", "29, 0", "30, 1", "89, 1", "90, 2", "250, 4", "3600, 60"})
        @DisplayName("round inteiro, sem piso: abaixo de 30 s fica 0")
        void derivaMinutoSemPiso(int seg, int minEsperado) {
            EtapaTreino etapa = mapper.toEntity(new EtapaInputDto(
                    "INTERVALADO", "Tiro", null, null, null, null, 1, null, null, null, seg));
            assertThat(etapa.getDuracaoMin()).isEqualTo(minEsperado);
        }

        @Test
        @DisplayName("sem duracaoSeg a etapa fica em minutos, como antes da V112")
        void semDuracaoSeg() {
            EtapaTreino etapa = mapper.toEntity(etapaInput(null, "4:00-4:10/km"));
            assertThat(etapa.getDuracaoSeg()).isNull();
            assertThat(etapa.getDuracaoMin()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("toDto(EtapaTreino)")
    class ToDto {
        @Test
        @DisplayName("devolve duracaoSeg ao lado de duracaoMin")
        void devolveDuracaoSeg() {
            EtapaTreino etapa = EtapaTreino.builder().tipoEtapa("INTERVALADO").duracaoMin(4).duracaoSeg(250).build();
            assertThat(mapper.toDto(etapa).duracaoSeg()).isEqualTo(250);
            assertThat(mapper.toDto(etapa).duracaoMin()).isEqualTo(4);
        }
    }

    private EtapaInputDto etapaInput(String fcAlvoEtapa, String ritmoAlvo) {
        return new EtapaInputDto("PRINCIPAL", "Tiro", 5, null, fcAlvoEtapa, ritmoAlvo, 1, null, null);
    }
}
