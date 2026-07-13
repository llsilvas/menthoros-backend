package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.fit.FitLapData;
import br.com.menthoros.backend.dto.fit.FitSessionData;
import br.com.menthoros.backend.services.impl.FitParseServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calibração do GAP interno contra a MATRIZ de fixtures (design D2 — task 3.1 de
 * fit-lap-derived-metrics): compara o {@code paceGap} calculado com a coluna "GAP médio" do
 * Garmin Connect, por volta, e registra erro médio + desvio máximo POR FIXTURE.
 *
 * <p><b>Hard gate executável:</b> enquanto {@code GapCalculator.HABILITADO} for {@code false},
 * a calibração REPORTA os números sem quebrar o build (a matriz está incompleta e o GAP não é
 * exposto). Se alguém ligar a flag, este teste passa a EXIGIR o critério duplo (erro médio
 * ≤ 3 s/km e máximo ≤ 5 s/km na faixa |g| ≤ 3%) — ligar sem matriz verde quebra a suíte.
 *
 * <p>Matriz atual: só a fixture principal (ondulado leve). Faltam: plano, net-up/net-down,
 * sem elevação (esteira), autopause, 2ª fonte — ver task 0.2.
 */
class GapCalibrationTest {

    private static final double ERRO_MEDIO_MAX_SEG_KM = 3.0;
    private static final double DESVIO_MAX_SEG_KM = 5.0;
    /** Faixa de gradiente da meta de calibração (|g| ≤ 3%). */
    private static final double GRADIENTE_FAIXA_CALIBRACAO = 0.03;

    private record Calibracao(String fixture, double erroMedioSegKm, double desvioMaxSegKm, int voltasComparadas) {}

    @Test
    @DisplayName("calibração por fixture: erro médio e desvio máximo do paceGap vs GAP do Garmin")
    void calibraContraMatrizDeFixtures() throws Exception {
        List<Calibracao> resultados = new ArrayList<>();
        resultados.add(calibrar("corrida-15km-16laps"));

        for (Calibracao c : resultados) {
            System.out.printf("CALIBRACAO[%s]: voltas=%d erroMedio=%.2fs/km desvioMax=%.2fs/km%n",
                    c.fixture(), c.voltasComparadas(), c.erroMedioSegKm(), c.desvioMaxSegKm());
            assertThat(c.voltasComparadas()).isGreaterThan(0);
            assertThat(c.erroMedioSegKm()).isFinite();

            if (GapCalculator.HABILITADO) {
                // Hard gate (design D2): flag ligada exige o critério duplo em TODA fixture.
                assertThat(c.erroMedioSegKm())
                        .as("erro médio da fixture %s com GAP HABILITADO", c.fixture())
                        .isLessThanOrEqualTo(ERRO_MEDIO_MAX_SEG_KM);
                assertThat(c.desvioMaxSegKm())
                        .as("desvio máximo por volta da fixture %s com GAP HABILITADO", c.fixture())
                        .isLessThanOrEqualTo(DESVIO_MAX_SEG_KM);
            }
        }
    }

    private Calibracao calibrar(String fixture) throws Exception {
        FitSessionData dados = parse("/fit/" + fixture + ".fit");
        List<Duration> gapsGarmin = gapsDoCsv("/fit/" + fixture + "-garmin.csv");
        assertThat(gapsGarmin).hasSameSizeAs(dados.laps());

        List<Double> errosSegKm = new ArrayList<>();
        for (int i = 0; i < dados.laps().size(); i++) {
            FitLapData lap = dados.laps().get(i);
            Duration paceBruto = paceBruto(lap);
            Double custo = GapCalculator.custoRelativo(lap.subidaMetros(), lap.descidaMetros(), lap.distanciaKm());
            if (paceBruto == null || custo == null) {
                continue; // volta fora de sanidade (ex.: lap 16 com 0 km) não entra na calibração
            }
            double gradiente = (lap.subidaMetros() - lap.descidaMetros()) / (lap.distanciaKm() * 1000.0);
            if (Math.abs(gradiente) > GRADIENTE_FAIXA_CALIBRACAO) {
                continue; // meta de calibração é definida na faixa |g| <= 3%
            }
            Duration paceGap = GapCalculator.paceGap(paceBruto, lap.subidaMetros(), lap.descidaMetros(), lap.distanciaKm());
            double erro = Math.abs(paceGap.toSeconds() - gapsGarmin.get(i).toSeconds());
            System.out.printf("  volta %2d: dist=%.3f sub=%d desc=%d bruto=%ds gap=%ds garmin=%ds erro=%.0fs%n",
                    lap.ordem(), lap.distanciaKm(), lap.subidaMetros(), lap.descidaMetros(),
                    paceBruto.toSeconds(), paceGap.toSeconds(), gapsGarmin.get(i).toSeconds(), erro);
            errosSegKm.add(erro);
        }

        double erroMedio = errosSegKm.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        double desvioMax = errosSegKm.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
        return new Calibracao(fixture, erroMedio, desvioMax, errosSegKm.size());
    }

    private FitSessionData parse(String resource) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertThat(in).as("fixture %s no classpath", resource).isNotNull();
            return new FitParseServiceImpl().parse(in);
        }
    }

    /** Distância mínima para o pace da volta ser significativo (lap de arremate de 4 m não calibra nada). */
    private static final double DISTANCIA_MINIMA_KM = 0.2;

    private Duration paceBruto(FitLapData lap) {
        if (lap.distanciaKm() == null || lap.distanciaKm() < DISTANCIA_MINIMA_KM
                || lap.duracao() == null || lap.duracao().isZero()) {
            return null;
        }
        return Duration.ofSeconds(Math.round(lap.duracao().toSeconds() / lap.distanciaKm()));
    }

    /** Lê a coluna "GAP médio min/km" (índice 5) do CSV exportado do Garmin Connect. */
    private List<Duration> gapsDoCsv(String resource) throws Exception {
        List<Duration> gaps = new ArrayList<>();
        try (InputStream in = getClass().getResourceAsStream(resource);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String linha = reader.readLine(); // header
            while ((linha = reader.readLine()) != null) {
                String[] colunas = linha.split("\",\"");
                if (colunas.length < 6 || colunas[0].replace("\"", "").equals("Resumo")) {
                    continue;
                }
                gaps.add(parseMinSeg(colunas[5]));
            }
        }
        return gaps;
    }

    private Duration parseMinSeg(String valor) {
        String[] partes = valor.replace("\"", "").split(":");
        return Duration.ofSeconds(Long.parseLong(partes[0]) * 60 + Long.parseLong(partes[1]));
    }
}
