package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.domain.workout.StructuredWorkout;
import br.com.menthoros.backend.dto.intervalsicu.IcuEventDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.EtapaTreino;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.services.IntervalsIcuClient;
import br.com.menthoros.backend.services.helper.IntervalsIcuFcAlvoResolver;
import br.com.menthoros.backend.services.helper.IntervalsIcuWorkoutConverter;
import br.com.menthoros.backend.services.helper.ZonaTreinoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O que o relógio recebe tem que ser o bpm que o treinador prescreveu.
 *
 * <p>Cobre a costura converter → adapter porque é exatamente onde o defeito vivia: cada lado estava
 * certo isoladamente. O converter produzia um alvo relativo fiel ao texto do plano (base %LTHR) e o
 * adapter o serializava fielmente como {@code %hr} — canal que o padrão Garmin define como %FCmax.
 * Nenhum teste de uma classe só pega isso; é preciso olhar o número que sai da ponta.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Alvo de FC chega ao relógio em bpm absoluto")
class IntervalsIcuFcAlvoAbsolutoTest {

    private static final String TOKEN = "token-123";
    private static final String ATHLETE_ID = "i12345";
    private static final LocalDate DATA = LocalDate.of(2026, 8, 20);

    /** Atleta real do banco de dev, o mesmo que mediu +14,7% de inflação na task 0.1. */
    private static final int FC_LIMIAR = 170;
    private static final int FC_MAXIMA = 195;

    @Mock
    private IntervalsIcuClient client;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IntervalsIcuWorkoutConverter converter =
            new IntervalsIcuWorkoutConverter(
                    new IntervalsIcuFcAlvoResolver(new ZonaTreinoService()));

    private IntervalsIcuAdapter adapter;
    private IntegracaoExterna conexao;

    @BeforeEach
    void setUp() {
        adapter = new IntervalsIcuAdapter(client, objectMapper);
        conexao = new IntegracaoExterna();
        conexao.setAccessToken(TOKEN);
        conexao.setExternalAthleteId(ATHLETE_ID);
    }

    @Test
    @DisplayName("percentual do plano vira bpm na base do domínio (%LTHR), não da FC máxima")
    void percentualViraBpmNaBaseDoDominio() {
        TreinoPlanejado treino = treinoComEtapaDeFc("60-70% FCmax");

        JsonNode hr = primeiroHrDoPayload(treino);

        // Intenção: 60-70% do LTHR (170) = 102-119 bpm.
        // Enviado como "%hr", o relógio lia 60-70% da FCmax (195) = 117-137 bpm: +14,7%.
        assertThat(hr.get("units").asText()).isEqualTo("bpm");
        assertThat(hr.get("start").asInt()).isEqualTo(102);
        assertThat(hr.get("end").asInt()).isEqualTo(119);
    }

    @Test
    @DisplayName("zona do plano vira a faixa em bpm que o ZonaTreinoService calcula para a zona")
    void zonaViraBpmDaFaixaCalculada() {
        TreinoPlanejado treino = treinoComZonaAlvo("Z2");

        JsonNode hr = primeiroHrDoPayload(treino);

        // Z2 = 85-89% do LTHR (170), o mesmo que ZonaTreinoService calcula. Valores fixados de
        // propósito: só a igualdade com o serviço deixaria as duas pontas quebrarem juntas.
        assertThat(hr.get("units").asText()).isEqualTo("bpm");
        assertThat(hr.get("start").asInt()).isEqualTo(145);
        assertThat(hr.get("end").asInt()).isEqualTo(151);

        ZonaTreinoService.ZonaFC z2 =
                new ZonaTreinoService().calcularZonasFC(FC_MAXIMA, FC_LIMIAR).get(1);
        assertThat(hr.get("start").asInt()).isEqualTo(z2.fcMin());
        assertThat(hr.get("end").asInt()).isEqualTo(z2.fcMax());
    }

    @Test
    @DisplayName("nenhum alvo relativo trafega: nem %hr, nem hr_zone")
    void nenhumAlvoRelativoTrafega() {
        TreinoPlanejado treino = treinoComZonaAlvo("Z4");

        String payload = payloadDoPush(treino).toString();

        assertThat(payload).doesNotContain("%hr").doesNotContain("hr_zone");
    }

    // ===== Helpers =====

    private JsonNode primeiroHrDoPayload(TreinoPlanejado treino) {
        JsonNode step = payloadDoPush(treino).get("workout_doc").get("steps").get(0);
        assertThat(step.has("hr")).as("etapa prescrita por FC deve levar meta de FC").isTrue();
        return step.get("hr");
    }

    private JsonNode payloadDoPush(TreinoPlanejado treino) {
        StructuredWorkout workout = converter.converter(treino).orElseThrow().workout();

        when(client.listarEventos(TOKEN, ATHLETE_ID, DATA, DATA)).thenReturn(List.of());
        when(client.criarEvento(eq(TOKEN), eq(ATHLETE_ID), any()))
                .thenReturn(new IcuEventDto(1L, workout.externalId(), workout.name(), DATA + "T00:00:00"));

        adapter.push(conexao, workout, null);

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(client).criarEvento(eq(TOKEN), eq(ATHLETE_ID), captor.capture());
        return captor.getValue();
    }

    private TreinoPlanejado treinoComEtapaDeFc(String fcAlvoEtapa) {
        EtapaTreino etapa = EtapaTreino.builder()
                .id(UUID.randomUUID())
                .ordem(1)
                .tipoEtapa("CONTINUO")
                .descricaoEtapa("Base aeróbica")
                .duracaoMin(40)
                .fcAlvoEtapa(fcAlvoEtapa)
                .build();

        TreinoPlanejado treino = treinoBase();
        treino.setEtapas(List.of(etapa));
        return treino;
    }

    /**
     * Zona só chega ao modelo canônico por {@code treino.zonaAlvo} — o parser de etapa não reconhece
     * {@code "Z2"}, e o campo do treino é o único caminho vivo até um alvo por zona.
     * Sem etapas, o converter cai no step único, que é onde a zona é lida.
     */
    private TreinoPlanejado treinoComZonaAlvo(String zonaAlvo) {
        TreinoPlanejado treino = treinoBase();
        treino.setZonaAlvo(zonaAlvo);
        treino.setEtapas(List.of());
        return treino;
    }

    private TreinoPlanejado treinoBase() {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setId(UUID.randomUUID());
        treino.setTipoTreino(TipoTreino.CONTINUO);
        treino.setDataTreino(DATA);
        treino.setDuracaoMin(Duration.ofMinutes(40));
        treino.setAtleta(Atleta.builder()
                .id(UUID.randomUUID())
                .fcLimiar(FC_LIMIAR)
                .fcMaxima(FC_MAXIMA)
                .build());
        return treino;
    }
}
