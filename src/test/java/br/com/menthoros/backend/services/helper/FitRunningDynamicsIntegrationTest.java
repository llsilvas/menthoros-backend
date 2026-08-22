package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.fit.FitSessionData;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.services.IngestaoTreinoRealizadoService;
import br.com.menthoros.backend.services.impl.FitParseServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validação integrada da change fit-running-dynamics-ingestion (task 5.1): fixture real
 * (parse → {@link FitTreinoPersister} real, sem duplicar lógica) → comparação campo a campo
 * com o CSV do Garmin Connect ({@code activity_23558283865.csv}, mesma atividade de
 * {@code corrida-15km-16laps.fit} — arquivos idênticos, exportados de novo com as colunas de
 * running dynamics que a fixture original não tinha).
 *
 * <p><b>Achados da validação (registrados em tasks.md 5.1):</b> a coluna "Tempo" do CSV do
 * Garmin Connect corresponde a {@code totalTimerTime} ({@code tempoMovimento}), não à duração
 * elapsed bruta — confirmado por correspondência exata (sub-segundo) em todas as voltas e na
 * sessão. A convenção de {@code getAvgStanceTimeBalance()} é o % do pé ESQUERDO ("E" no CSV),
 * confirmando a assumption do proposal.md.
 */
@ExtendWith(MockitoExtension.class)
class FitRunningDynamicsIntegrationTest {

    @Mock private AtletaRepository atletaRepository;
    @Mock private TreinoMapper treinoMapper;
    @Mock private IngestaoTreinoRealizadoService ingestaoTreinoRealizadoService;

    private FitTreinoPersister service;
    private UUID tenantId;
    private UUID atletaId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        service = new FitTreinoPersister(atletaRepository, treinoMapper, ingestaoTreinoRealizadoService);

        Atleta atleta = mock(Atleta.class);
        lenient().when(atleta.getId()).thenReturn(atletaId);
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
        when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
        when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("sessão (Resumo do CSV): running dynamics batem campo a campo com o Garmin Connect")
    void sessaoBateComCsvResumo() throws Exception {
        TreinoRealizado treino = persistirFixtureReal();

        // CSV "Resumo": GCT=254, Equilíbrio="51,1% E / 48,9% D" (E=51.1, confirma a assumption),
        // Passada=0,97, Oscilação=10,7, Proporção=10,9, Calorias=1.222, Temperatura=21,0.
        assertThat(treino.getGctMedioMs()).isEqualTo(254);
        assertThat(treino.getGctEquilibrioPct()).isEqualByComparingTo("51.1");
        assertThat(treino.getPassadaMediaM()).isEqualByComparingTo("0.97");
        assertThat(treino.getOscilacaoVerticalCm()).isEqualByComparingTo("10.7");
        assertThat(treino.getProporcaoVerticalPct()).isEqualByComparingTo("10.9");
        assertThat(treino.getCalorias()).isEqualTo(1222);
        assertThat(treino.getTemperaturaMediaC()).isEqualByComparingTo("21.0");

        // Achado: a coluna "Tempo" do CSV (1:33:33 -> 5613s) corresponde a tempoMovimento
        // (totalTimerTime), não à duracao elapsed (totalElapsedTime, maior — inclui paradas
        // completas que o Garmin Connect não expõe nesta tabela de voltas).
        assertThat(treino.getTempoMovimento()).isCloseTo(Duration.ofSeconds(5613), Duration.ofSeconds(1));
        assertThat(treino.getDuracaoMin()).isNotEqualTo(treino.getTempoMovimento());
    }

    @Test
    @DisplayName("volta 1 (sem pausa): running dynamics batem com o CSV; tempoMovimento == duracao")
    void volta1SemPausaBateComCsv() throws Exception {
        TreinoRealizado treino = persistirFixtureReal();
        var lap1 = treino.getEtapasRealizadas().get(0);

        // CSV volta 1: GCT=259, Equilíbrio="51,3% E", Passada=0,93, Oscilação=11,2, Proporção=11,9, Temp=24,0.
        assertThat(lap1.getGctMedioMs()).isEqualTo(259);
        assertThat(lap1.getGctEquilibrioPct()).isEqualByComparingTo("51.3");
        assertThat(lap1.getPassadaMediaM()).isEqualByComparingTo("0.93");
        assertThat(lap1.getOscilacaoVerticalCm()).isEqualByComparingTo("11.2");
        assertThat(lap1.getProporcaoVerticalPct()).isEqualByComparingTo("11.9");
        assertThat(lap1.getTemperaturaMediaC()).isEqualByComparingTo("24.0");
        assertThat(lap1.getTempoMovimento()).isEqualTo(lap1.getDuracao());
    }

    @Test
    @DisplayName("D6/CA7 com dado real: volta 10 (mesma pausa documentada em fit-lap-derived-metrics) tem pace corrigido")
    void volta10ComPausaRealTemPaceCorrigido() throws Exception {
        TreinoRealizado treino = persistirFixtureReal();
        var lap10 = treino.getEtapasRealizadas().get(9);

        // CSV volta 10: GCT=251, Equilíbrio="51,0% E", Passada=0,99, Oscilação=10,8, Proporção=10,7, Temp=21,0.
        assertThat(lap10.getGctMedioMs()).isEqualTo(251);
        assertThat(lap10.getGctEquilibrioPct()).isEqualByComparingTo("51.0");
        assertThat(lap10.getPassadaMediaM()).isEqualByComparingTo("0.99");
        assertThat(lap10.getOscilacaoVerticalCm()).isEqualByComparingTo("10.8");
        assertThat(lap10.getProporcaoVerticalPct()).isEqualByComparingTo("10.7");
        assertThat(lap10.getTemperaturaMediaC()).isEqualByComparingTo("21.0");

        // Achado real (confirma o que fit-lap-derived-metrics documentou com números aproximados):
        // duracao (elapsed) = 611,069s; tempoMovimento (timer time) = 363,746s — pausa real de ~247s.
        assertThat(lap10.getDuracao()).isEqualTo(Duration.ofSeconds(611).plusMillis(69));
        assertThat(lap10.getTempoMovimento()).isEqualTo(Duration.ofSeconds(363).plusMillis(746));

        // Pace corrigido (D6): usa tempoMovimento -> 6:03/km, não os 10:11/km que o elapsed bruto daria.
        assertThat(lap10.getPaceMedia()).isEqualTo(Duration.ofMinutes(6).plusSeconds(3));
        assertThat(lap10.getVelocidadeMedia()).isEqualByComparingTo("9.90");
    }

    private TreinoRealizado persistirFixtureReal() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fit/corrida-15km-16laps.fit")) {
            FitSessionData dados = new FitParseServiceImpl().parse(in);
            service.persistir(atletaId, dados);
        }
        ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
        verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
        return captor.getValue();
    }
}
