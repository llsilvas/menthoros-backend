package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.input.EtapaInputDto;
import br.com.menthoros.backend.entity.EtapaTreino;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
        @DisplayName("blocoId nunca vem do cliente — segue ignorado de propósito")
        void blocoIdIgnorado() {
            EtapaTreino etapa = mapper.toEntity(etapaInput("140-150 bpm", "5:00-5:15/km"));

            assertThat(etapa.getBlocoId()).isNull();
        }
    }

    private EtapaInputDto etapaInput(String fcAlvoEtapa, String ritmoAlvo) {
        return new EtapaInputDto("PRINCIPAL", "Tiro", 5, null, fcAlvoEtapa, ritmoAlvo, 1, null, null);
    }
}
