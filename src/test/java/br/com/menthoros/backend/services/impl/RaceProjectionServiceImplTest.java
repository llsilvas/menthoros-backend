package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.RaceProjectionRequestDto;
import br.com.menthoros.backend.dto.output.AthleteProjectionViewDto;
import br.com.menthoros.backend.dto.output.RaceProjectionSnapshotOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.RaceProjectionSnapshot;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.mapper.AthleteProfileMapper;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.repository.RaceProjectionSnapshotRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.skills.race.AthleteProfile;
import br.com.menthoros.backend.skills.race.RaceProjection;
import br.com.menthoros.backend.skills.race.RaceProjectionInput;
import br.com.menthoros.backend.skills.race.RaceProjectionOutput;
import br.com.menthoros.backend.skills.race.RaceProjectionSkill;
import br.com.menthoros.backend.skills.race.enums.DataQuality;
import br.com.menthoros.backend.skills.race.enums.PeriodizationPhase;
import br.com.menthoros.backend.skills.race.enums.ProjectionConfidence;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceProjectionServiceImplTest {

    @Mock
    private AtletaRepository atletaRepository;
    @Mock
    private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock
    private ProvaRepository provaRepository;
    @Mock
    private RaceProjectionSnapshotRepository snapshotRepository;
    @Mock
    private RaceProjectionSkill raceProjectionSkill;
    @Mock
    private AthleteProfileMapper athleteProfileMapper;

    @Captor
    private ArgumentCaptor<RaceProjectionInput> inputCaptor;

    // ObjectMapper real — a serialização/deserialização do snapshot faz parte do comportamento sob teste.
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    private RaceProjectionServiceImpl service;

    private UUID tenantId;
    private UUID atletaId;
    private UUID coachId;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        service = new RaceProjectionServiceImpl(
                atletaRepository, treinoRealizadoRepository, provaRepository,
                snapshotRepository, raceProjectionSkill, athleteProfileMapper, objectMapper);

        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        coachId = UUID.randomUUID();
        atleta = Atleta.builder().id(atletaId).build();
    }

    @Nested
    @DisplayName("generate")
    class Generate {

        @Test
        @DisplayName("monta input, persiste snapshot e retorna projeção quando provaId é nulo")
        void happyPathSemProva() {
            stubAtletaEncontrado();
            when(treinoRealizadoRepository
                    .findByAtletaAndDataTreinoGreaterThanEqualOrderByDataTreinoDesc(eq(atleta), any()))
                    .thenReturn(List.of(treino(LocalDate.now().minusWeeks(2), TipoTreino.LONGO)));
            when(provaRepository.findByAtletaOrderByDataProvaAsc(atleta)).thenReturn(List.of());
            AthleteProfile profile = athleteProfile();
            when(athleteProfileMapper.from(atleta)).thenReturn(profile);
            when(raceProjectionSkill.execute(any())).thenReturn(outputComConfidencias(
                    ProjectionConfidence.MEDIUM, ProjectionConfidence.LOW));

            RaceProjectionSnapshotOutputDto dto = service.generate(
                    atletaId, tenantId, coachId, request(null, List.of(10000, 21097), 8));

            assertNotNull(dto);
            // confiança global = menor entre as projeções (MEDIUM, LOW) → LOW
            assertEquals(ProjectionConfidence.LOW, dto.confidence());
            verify(snapshotRepository).save(any(RaceProjectionSnapshot.class));
            verify(provaRepository, never()).findById(any());

            RaceProjectionInput input = capturarInput();
            assertSame(profile, input.athleteProfile());
            assertEquals(List.of(10000, 21097), input.targetDistances());
            assertEquals(8, input.weeksToRaceOverride());
        }

        @Test
        @DisplayName("D8: cancelado não conta no histórico, NULL conta [task 7.7]")
        void canceladoNaoContaNullConta() {
            stubAtletaEncontrado();
            TreinoRealizado cancelado = treino(LocalDate.now().minusWeeks(1), TipoTreino.LONGO);
            cancelado.setStatusSincronizacao(br.com.menthoros.backend.enums.StatusSincronizacao.CANCELADO);
            TreinoRealizado semStatus = treino(LocalDate.now().minusWeeks(2), TipoTreino.LONGO);
            semStatus.setStatusSincronizacao(null);

            when(treinoRealizadoRepository
                    .findByAtletaAndDataTreinoGreaterThanEqualOrderByDataTreinoDesc(eq(atleta), any()))
                    .thenReturn(List.of(cancelado, semStatus));
            when(provaRepository.findByAtletaOrderByDataProvaAsc(atleta)).thenReturn(List.of());
            when(athleteProfileMapper.from(atleta)).thenReturn(athleteProfile());
            when(raceProjectionSkill.execute(any())).thenReturn(outputComConfidencias(ProjectionConfidence.HIGH));

            service.generate(atletaId, tenantId, coachId, request(null, List.of(10000), 8));

            RaceProjectionInput input = capturarInput();
            assertEquals(1, input.trainingHistory().workouts().size(),
                    "só o treino sem status (NULL) conta — o CANCELADO fica de fora");
        }

        @Test
        @DisplayName("converte unidades do treino corretamente (km→m, Duration→segundos)")
        void converteUnidadesDoTreino() {
            stubAtletaEncontrado();
            TreinoRealizado tr = new TreinoRealizado();
            tr.setDataTreino(LocalDate.now().minusWeeks(1));
            tr.setTipoTreino(TipoTreino.LONGO);
            tr.setDistanciaKm(new BigDecimal("10.0"));
            tr.setDuracaoMin(Duration.ofMinutes(50));   // 3000 s
            tr.setPaceMedia(Duration.ofSeconds(300));   // 5:00/km
            tr.setFcMedia(150);
            tr.setTssCalculado(80);
            tr.setPercepcaoEsforco(6);
            stubHistorico(List.of(tr), List.of());
            stubSkillSimples();

            service.generate(atletaId, tenantId, coachId, request(null, List.of(10000), 6));

            var w = capturarInput().trainingHistory().workouts().get(0);
            assertEquals(10000, w.distanceM());
            assertEquals(3000, w.durationSeconds());
            assertEquals(300, w.avgPaceSecPerKm());
            assertEquals(150, w.avgHr());
        }

        @Test
        @DisplayName("exclui treinos sem distância ou sem pace do histórico")
        void filtraTreinosIncompletos() {
            stubAtletaEncontrado();
            TreinoRealizado completo = treino(LocalDate.now().minusWeeks(1), TipoTreino.LONGO);
            TreinoRealizado semDistancia = treino(LocalDate.now().minusWeeks(1), TipoTreino.LONGO);
            semDistancia.setDistanciaKm(null);
            TreinoRealizado semPace = treino(LocalDate.now().minusWeeks(1), TipoTreino.LONGO);
            semPace.setPaceMedia(null);
            stubHistorico(List.of(completo, semDistancia, semPace), List.of());
            stubSkillSimples();

            service.generate(atletaId, tenantId, coachId, request(null, List.of(10000), 6));

            assertEquals(1, capturarInput().trainingHistory().workouts().size());
        }

        @Test
        @DisplayName("classifica DataQuality FULL com 12+ sessões TEMPO_RUN/LONGO")
        void dataQualityFull() {
            assertDataQuality(12, DataQuality.FULL);
        }

        @Test
        @DisplayName("classifica DataQuality PARTIAL entre 6 e 11 sessões")
        void dataQualityPartial() {
            assertDataQuality(6, DataQuality.PARTIAL);
        }

        @Test
        @DisplayName("classifica DataQuality SPARSE com menos de 6 sessões")
        void dataQualitySparse() {
            assertDataQuality(1, DataQuality.SPARSE);
        }

        @Test
        @DisplayName("resolve distância de prova padrão pelo enum (KM_21 → 21097 m)")
        void resolveDistanciaPorEnum() {
            stubAtletaEncontrado();
            Prova passada = provaRealizada();
            passada.setDistancia(DistanciaProva.KM_21);
            passada.setDistanciaKm(null);
            stubHistorico(List.of(), List.of(passada));
            stubSkillSimples();

            service.generate(atletaId, tenantId, coachId, request(null, List.of(21097), 6));

            assertEquals(21097, capturarInput().pastRaces().get(0).distanceM());
        }

        @Test
        @DisplayName("resolve distância customizada de prova por distanciaKm (×1000)")
        void resolveDistanciaCustomizada() {
            stubAtletaEncontrado();
            Prova passada = provaRealizada();
            passada.setDistanciaKm(new BigDecimal("15.0"));
            stubHistorico(List.of(), List.of(passada));
            stubSkillSimples();

            service.generate(atletaId, tenantId, coachId, request(null, List.of(15000), 6));

            assertEquals(15000, capturarInput().pastRaces().get(0).distanceM());
        }

        @Test
        @DisplayName("exclui provas não realizadas ou sem tempo registrado")
        void filtraProvasNaoRealizadas() {
            stubAtletaEncontrado();
            Prova realizada = provaRealizada();
            Prova naoRealizada = provaRealizada();
            naoRealizada.setFoiRealizada(false);
            Prova semTempo = provaRealizada();
            semTempo.setTempoRealizado(null);
            stubHistorico(List.of(), List.of(realizada, naoRealizada, semTempo));
            stubSkillSimples();

            service.generate(atletaId, tenantId, coachId, request(null, List.of(10000), 6));

            assertEquals(1, capturarInput().pastRaces().size());
        }

        @Test
        @DisplayName("carrega e valida a prova quando provaId é informado")
        void carregaProvaQuandoInformada() {
            stubAtletaEncontrado();
            UUID provaId = UUID.randomUUID();
            Prova prova = provaRealizada();
            prova.setId(provaId);
            when(provaRepository.findById(provaId)).thenReturn(Optional.of(prova));
            stubHistorico(List.of(), List.of());
            stubSkillSimples();

            RaceProjectionSnapshotOutputDto dto = service.generate(
                    atletaId, tenantId, coachId, request(provaId, List.of(10000), 6));

            assertEquals(provaId, dto.provaId());
        }

        @Test
        @DisplayName("lança ResourceNotFoundException quando o atleta não existe")
        void atletaInexistente() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                    service.generate(atletaId, tenantId, coachId, request(null, List.of(10000), 6)));

            verifyNoInteractions(raceProjectionSkill);
            verify(snapshotRepository, never()).save(any());
        }

        @Test
        @DisplayName("lança ResourceNotFoundException quando a prova não existe")
        void provaInexistente() {
            stubAtletaEncontrado();
            UUID provaId = UUID.randomUUID();
            when(provaRepository.findById(provaId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                    service.generate(atletaId, tenantId, coachId, request(provaId, List.of(10000), 6)));
        }

        @Test
        @DisplayName("rejeita prova de outro atleta como não encontrada")
        void provaDeOutroAtleta() {
            stubAtletaEncontrado();
            UUID provaId = UUID.randomUUID();
            Prova prova = provaRealizada();
            prova.setId(provaId);
            prova.setAtleta(Atleta.builder().id(UUID.randomUUID()).build());
            when(provaRepository.findById(provaId)).thenReturn(Optional.of(prova));

            assertThrows(ResourceNotFoundException.class, () ->
                    service.generate(atletaId, tenantId, coachId, request(provaId, List.of(10000), 6)));
        }

        @Test
        @DisplayName("histórico enviado à skill usa o tipo prescrito quando o realizado veio vinculado ao planejado")
        void usaTipoPrescritoNoHistorico() {
            // O tipo do realizado vindo de integração é inferido por heurística e erra; a projeção
            // pergunta "que sessão foi essa?", então vale a prescrição do coach.
            stubAtletaEncontrado();
            TreinoRealizado longaoMalClassificado =
                    vinculado(treino(LocalDate.now().minusWeeks(1), TipoTreino.TEMPO_RUN), TipoTreino.LONGO);
            stubHistorico(List.of(longaoMalClassificado), List.of());
            stubSkillSimples();

            service.generate(atletaId, tenantId, coachId, request(null, List.of(10000), 6));

            assertEquals(TipoTreino.LONGO, capturarInput().trainingHistory().workouts().get(0).type());
        }

        @Test
        @DisplayName("sessões qualificantes mal classificadas pelo sync ainda contam para o DataQuality")
        void dataQualityContaSessoesVinculadas() {
            // DataQuality é derivado do type() do WorkoutSummary: com o tipo inferido (FACIL, não
            // qualificante) as 6 sessões cairiam em SPARSE e a projeção sairia com confiança baixa.
            stubAtletaEncontrado();
            List<TreinoRealizado> treinos = IntStream.range(0, 6)
                    .mapToObj(i -> vinculado(
                            treino(LocalDate.now().minusWeeks(i + 1L), TipoTreino.FACIL), TipoTreino.LONGO))
                    .toList();
            stubHistorico(treinos, List.of());
            stubSkillSimples();

            service.generate(atletaId, tenantId, coachId, request(null, List.of(10000), 6));

            assertEquals(DataQuality.PARTIAL, capturarInput().trainingHistory().dataQuality());
        }

        private void assertDataQuality(int qtdSessoes, DataQuality esperado) {
            stubAtletaEncontrado();
            List<TreinoRealizado> treinos = new ArrayList<>();
            IntStream.range(0, qtdSessoes).forEach(i ->
                    treinos.add(treino(LocalDate.now().minusWeeks(i + 1L), TipoTreino.LONGO)));
            stubHistorico(treinos, List.of());
            stubSkillSimples();

            service.generate(atletaId, tenantId, coachId, request(null, List.of(10000), 6));

            assertEquals(esperado, capturarInput().trainingHistory().dataQuality());
        }
    }

    @Nested
    @DisplayName("getHistory")
    class GetHistory {

        @Test
        @DisplayName("retorna snapshots desserializados em ordem decrescente")
        void retornaHistorico() {
            stubAtletaEncontrado();
            UUID provaId = UUID.randomUUID();
            RaceProjectionSnapshot snapshot = snapshotPersistido();
            when(snapshotRepository.findByAthleteIdAndRaceIdOrderByGeneratedAtDesc(atletaId, provaId))
                    .thenReturn(List.of(snapshot));

            List<RaceProjectionSnapshotOutputDto> historico = service.getHistory(atletaId, provaId, tenantId);

            assertEquals(1, historico.size());
            assertFalse(historico.get(0).projections().isEmpty());
        }

        @Test
        @DisplayName("lança ResourceNotFoundException quando o atleta não existe")
        void atletaInexistente() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                    service.getHistory(atletaId, UUID.randomUUID(), tenantId));
        }
    }

    @Nested
    @DisplayName("getOfficial")
    class GetOfficial {

        @Test
        @DisplayName("retorna a projeção oficial quando existe")
        void retornaOficial() {
            stubAtletaEncontrado();
            UUID provaId = UUID.randomUUID();
            when(snapshotRepository.findOfficialByAthleteIdAndRaceId(atletaId, provaId))
                    .thenReturn(Optional.of(snapshotPersistido()));

            Optional<RaceProjectionSnapshotOutputDto> oficial = service.getOfficial(atletaId, provaId, tenantId);

            assertTrue(oficial.isPresent());
        }

        @Test
        @DisplayName("retorna vazio quando não há projeção oficial")
        void semOficial() {
            stubAtletaEncontrado();
            UUID provaId = UUID.randomUUID();
            when(snapshotRepository.findOfficialByAthleteIdAndRaceId(atletaId, provaId))
                    .thenReturn(Optional.empty());

            assertTrue(service.getOfficial(atletaId, provaId, tenantId).isEmpty());
        }
    }

    @Nested
    @DisplayName("markAsOfficial")
    class MarkAsOfficial {

        @Test
        @DisplayName("limpa oficial anterior, marca como oficial e registra revisão do coach")
        void marcaComoOficial() {
            stubAtletaEncontrado();
            UUID snapshotId = UUID.randomUUID();
            UUID provaId = UUID.randomUUID();
            RaceProjectionSnapshot snapshot = snapshotPersistido();
            when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));

            service.markAsOfficial(snapshotId, atletaId, provaId, tenantId, coachId);

            verify(snapshotRepository).clearOfficialByAthleteIdAndRaceId(atletaId, provaId);
            verify(snapshotRepository).save(snapshot);
            assertTrue(snapshot.isOfficial());
            assertNotNull(snapshot.getCoachReviewedAt());
        }

        @Test
        @DisplayName("lança ResourceNotFoundException quando o snapshot não existe")
        void snapshotInexistente() {
            stubAtletaEncontrado();
            UUID snapshotId = UUID.randomUUID();
            when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                    service.markAsOfficial(snapshotId, atletaId, UUID.randomUUID(), tenantId, coachId));

            verify(snapshotRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejeita snapshot de outro atleta como não encontrado")
        void snapshotDeOutroAtleta() {
            stubAtletaEncontrado();
            UUID snapshotId = UUID.randomUUID();
            RaceProjectionSnapshot snapshot = snapshotPersistido();
            snapshot.setAthlete(Atleta.builder().id(UUID.randomUUID()).build());
            when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));

            assertThrows(ResourceNotFoundException.class, () ->
                    service.markAsOfficial(snapshotId, atletaId, UUID.randomUUID(), tenantId, coachId));
        }
    }

    @Nested
    @DisplayName("getAthleteView")
    class GetAthleteView {

        @Test
        @DisplayName("retorna a visão do atleta quando a projeção oficial foi revisada")
        void retornaVisaoRevisada() {
            stubAtletaEncontrado();
            UUID provaId = UUID.randomUUID();
            RaceProjectionSnapshot snapshot = snapshotPersistido();
            snapshot.setCoachReviewedAt(java.time.Instant.now());
            when(snapshotRepository.findOfficialByAthleteIdAndRaceId(atletaId, provaId))
                    .thenReturn(Optional.of(snapshot));

            Optional<AthleteProjectionViewDto> view = service.getAthleteView(atletaId, provaId, tenantId);

            assertTrue(view.isPresent());
        }

        @Test
        @DisplayName("retorna vazio quando a projeção oficial ainda não foi revisada pelo coach")
        void semRevisao() {
            stubAtletaEncontrado();
            UUID provaId = UUID.randomUUID();
            RaceProjectionSnapshot snapshot = snapshotPersistido();
            snapshot.setCoachReviewedAt(null);
            when(snapshotRepository.findOfficialByAthleteIdAndRaceId(atletaId, provaId))
                    .thenReturn(Optional.of(snapshot));

            assertTrue(service.getAthleteView(atletaId, provaId, tenantId).isEmpty());
        }

        @Test
        @DisplayName("retorna vazio quando não há projeção oficial")
        void semOficial() {
            stubAtletaEncontrado();
            UUID provaId = UUID.randomUUID();
            when(snapshotRepository.findOfficialByAthleteIdAndRaceId(atletaId, provaId))
                    .thenReturn(Optional.empty());

            assertTrue(service.getAthleteView(atletaId, provaId, tenantId).isEmpty());
        }
    }

    // --- helpers ---

    private void stubAtletaEncontrado() {
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
    }

    private void stubHistorico(List<TreinoRealizado> treinos, List<Prova> provas) {
        when(treinoRealizadoRepository
                .findByAtletaAndDataTreinoGreaterThanEqualOrderByDataTreinoDesc(eq(atleta), any()))
                .thenReturn(treinos);
        when(provaRepository.findByAtletaOrderByDataProvaAsc(atleta)).thenReturn(provas);
    }

    private void stubSkillSimples() {
        when(athleteProfileMapper.from(atleta)).thenReturn(athleteProfile());
        when(raceProjectionSkill.execute(any())).thenReturn(outputComConfidencias(ProjectionConfidence.HIGH));
    }

    private RaceProjectionInput capturarInput() {
        verify(raceProjectionSkill).execute(inputCaptor.capture());
        return inputCaptor.getValue();
    }

    private RaceProjectionRequestDto request(UUID provaId, List<Integer> distancias, Integer weeksOverride) {
        var load = new RaceProjectionRequestDto.LoadProjectionData(
                40.0, 45.0, -5.0, LocalDate.now().plusWeeks(8), 8,
                PeriodizationPhase.BUILD_PEAK, 50.0, 5.0);
        return new RaceProjectionRequestDto(provaId, distancias, load, null, weeksOverride);
    }

    private TreinoRealizado treino(LocalDate data, TipoTreino tipo) {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setDataTreino(data);
        tr.setTipoTreino(tipo);
        tr.setDistanciaKm(new BigDecimal("12.0"));
        tr.setDuracaoMin(Duration.ofMinutes(60));
        tr.setPaceMedia(Duration.ofSeconds(300));
        tr.setFcMedia(150);
        tr.setTssCalculado(70);
        tr.setPercepcaoEsforco(6);
        return tr;
    }

    private TreinoRealizado vinculado(TreinoRealizado realizado, TipoTreino tipoPrescrito) {
        TreinoPlanejado planejado = new TreinoPlanejado();
        planejado.setTipoTreino(tipoPrescrito);
        realizado.setTreinoPlanejado(planejado);
        return realizado;
    }

    private Prova provaRealizada() {
        Prova prova = new Prova();
        prova.setId(UUID.randomUUID());
        prova.setAtleta(atleta);
        prova.setDistancia(DistanciaProva.KM_10);
        prova.setDistanciaKm(new BigDecimal("10.0"));
        prova.setFoiRealizada(true);
        prova.setTempoRealizado(Duration.ofHours(0).plusMinutes(45));
        prova.setDataProva(LocalDate.now().minusMonths(2));
        return prova;
    }

    private AthleteProfile athleteProfile() {
        return new AthleteProfile(atletaId, "João", "Silva", 182, 160,
                new BigDecimal("52.5"), NivelExperiencia.INTERMEDIARIO);
    }

    private RaceProjectionOutput outputComConfidencias(ProjectionConfidence... confidencias) {
        Map<Integer, RaceProjection> projections = new java.util.HashMap<>();
        int dist = 10000;
        for (ProjectionConfidence c : confidencias) {
            projections.put(dist, new RaceProjection(
                    dist, 2400L, 240, 2328L, 2472L, c, Boolean.TRUE));
            dist += 1000;
        }
        return new RaceProjectionOutput(projections, "Progressão consistente.",
                null, List.of("Premissa A"), "Nota do coach.", null);
    }

    private RaceProjectionSnapshot snapshotPersistido() {
        RaceProjectionSnapshot snapshot = new RaceProjectionSnapshot();
        snapshot.setAthlete(atleta);
        snapshot.setTenantId(tenantId);
        snapshot.setCoachId(coachId);
        snapshot.setConfidence(ProjectionConfidence.HIGH.name());
        snapshot.setWeeksToRaceAtGeneration(8);
        snapshot.setTrainingWeeksUsed(8);
        snapshot.setProjectionsJson(serializar(outputComConfidencias(ProjectionConfidence.HIGH)));
        return snapshot;
    }

    private String serializar(RaceProjectionOutput output) {
        try {
            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
