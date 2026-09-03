package br.com.menthoros.backend.services.plano;

import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.TipoProva;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.mapper.TreinoMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D2 (prova-no-plano-semanal): {@code construirTreinoProva} monta o DTO do treino PROVA que
 * {@code garantirProvasNaSemana} insere no plano — nome, distância, ritmo e duração derivados
 * da prova, nunca do LLM.
 */
class ProvaNoPlanoServiceTest {

    private ProvaNoPlanoService service;

    @BeforeEach
    void setUp() {
        TreinoMapper treinoMapper = new TreinoMapperImpl(null, null);
        service = new ProvaNoPlanoService(null, treinoMapper);
    }

    @Nested
    @DisplayName("construirTreinoProva")
    class ConstruirTreinoProva {

        @Test
        @DisplayName("com tempo objetivo: ritmo derivado, duração = tempo objetivo")
        void comTempoObjetivo() {
            Prova prova = provaCom(DistanciaProva.KM_21, BigDecimal.valueOf(21.1),
                    Duration.ofHours(1).plusMinutes(45));
            Atleta atleta = atletaComPace(null);

            TreinoPlanejadoLlmDto dto = service.construirTreinoProva(prova, atleta);

            assertThat(dto.tipoTreino()).isEqualTo("PROVA");
            assertThat(dto.descricao()).isEqualTo(prova.getNomeProva());
            assertThat(dto.distanciaKm()).isEqualTo(21.1);
            assertThat(dto.duracaoMin()).isEqualTo("01:45:00");
            assertThat(dto.ritmoAlvo()).isEqualTo("4:59");
            assertThat(dto.zonaAlvo()).isEqualTo("Zona 3-4");
            assertThat(dto.provaId()).isEqualTo(prova.getId());
            assertThat(dto.etapas()).isNull();
        }

        @Test
        @DisplayName("sem tempo objetivo: usa o pace de limiar do atleta")
        void semTempoObjetivoUsaLimiar() {
            Prova prova = provaCom(DistanciaProva.KM_10, BigDecimal.valueOf(10.0), null);
            Atleta atleta = atletaComPace(BigDecimal.valueOf(5.0)); // 5:00 min/km

            TreinoPlanejadoLlmDto dto = service.construirTreinoProva(prova, atleta);

            assertThat(dto.ritmoAlvo()).isEqualTo("5:00");
            assertThat(dto.duracaoMin()).isEqualTo("50:00"); // 10km * 5:00/km (TreinoMapper.durationToString omite a hora quando é 0)
        }

        @Test
        @DisplayName("sem tempo objetivo e sem pace de limiar: usa 6:00 min/km")
        void semTempoObjetivoESemLimiarUsa6Min() {
            Prova prova = provaCom(DistanciaProva.KM_5, BigDecimal.valueOf(5.0), null);
            Atleta atleta = atletaComPace(null);

            TreinoPlanejadoLlmDto dto = service.construirTreinoProva(prova, atleta);

            assertThat(dto.ritmoAlvo()).isEqualTo("6:00");
            assertThat(dto.duracaoMin()).isEqualTo("30:00"); // 5km * 6:00/km (TreinoMapper.durationToString omite a hora quando é 0)
        }

        @Test
        @DisplayName("dia da semana vem da data da prova")
        void diaDaSemanaDaData() {
            Prova prova = provaCom(DistanciaProva.KM_21, BigDecimal.valueOf(21.1), Duration.ofHours(1).plusMinutes(45));
            prova.setDataProva(LocalDate.of(2026, 12, 6)); // domingo
            Atleta atleta = atletaComPace(null);

            TreinoPlanejadoLlmDto dto = service.construirTreinoProva(prova, atleta);

            assertThat(dto.diaSemana()).isEqualTo("DOMINGO");
        }
    }

    private Prova provaCom(DistanciaProva distancia, BigDecimal distanciaKm, Duration tempoObjetivo) {
        Prova prova = Prova.builder()
                .id(UUID.randomUUID())
                .nomeProva("Meia Maratona de São Paulo")
                .dataProva(LocalDate.now().plusWeeks(10))
                .distancia(distancia)
                .distanciaKm(distanciaKm)
                .tipoProva(TipoProva.MEIA)
                .statusProva(ProvaStatus.PLANEJADA)
                .tempoObjetivo(tempoObjetivo)
                .build();
        return prova;
    }

    private Atleta atletaComPace(BigDecimal paceLimiar) {
        Atleta atleta = new Atleta();
        atleta.setPaceLimiar(paceLimiar);
        return atleta;
    }
}
