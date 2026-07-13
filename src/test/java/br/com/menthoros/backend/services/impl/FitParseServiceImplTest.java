package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.fit.FitSessionData;
import br.com.menthoros.backend.exception.FitParseException;
import com.garmin.fit.DateTime;
import com.garmin.fit.File;
import com.garmin.fit.FileEncoder;
import com.garmin.fit.FileIdMesg;
import com.garmin.fit.LapMesg;
import com.garmin.fit.SessionMesg;
import com.garmin.fit.Sport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes com arquivos .fit reais gerados via {@link FileEncoder} do próprio SDK — round-trip
 * genuíno (encode → parse), sem mocks do formato binário.
 */
@DisplayName("FitParseServiceImpl")
class FitParseServiceImplTest {

    private final FitParseServiceImpl service = new FitParseServiceImpl();

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("parse")
    class Parse {

        @Test
        @DisplayName("extrai dados completos de um .fit de corrida com laps")
        void extraiDadosCompletos() throws IOException {
            Instant inicio = Instant.parse("2026-07-01T10:00:00Z");
            byte[] fit = gerarFit(builder -> {
                builder.fileId.setSerialNumber(123456789L);

                SessionMesg session = new SessionMesg();
                session.setStartTime(new DateTime(inicio));
                session.setSport(Sport.RUNNING);
                session.setTotalElapsedTime(1800f); // 30 min
                session.setTotalDistance(5000f); // 5 km
                session.setAvgHeartRate((short) 150);
                session.setMaxHeartRate((short) 175);
                session.setTrainingStressScore(62f);
                builder.mesgs.add(session);

                LapMesg lap1 = new LapMesg();
                lap1.setTotalElapsedTime(900f);
                lap1.setTotalDistance(2500f);
                lap1.setAvgHeartRate((short) 148);
                lap1.setMaxHeartRate((short) 160);
                builder.mesgs.add(lap1);

                LapMesg lap2 = new LapMesg();
                lap2.setTotalElapsedTime(900f);
                lap2.setTotalDistance(2500f);
                lap2.setAvgHeartRate((short) 152);
                lap2.setMaxHeartRate((short) 175);
                builder.mesgs.add(lap2);
            });

            FitSessionData dados = service.parse(new ByteArrayInputStream(fit));

            assertThat(dados.serialNumber()).isEqualTo(123456789L);
            assertThat(dados.dataTreino()).isEqualTo(inicio.atZone(java.time.ZoneId.systemDefault()).toLocalDate());
            assertThat(dados.duracao()).isEqualTo(java.time.Duration.ofMinutes(30));
            assertThat(dados.distanciaKm()).isEqualTo(5.0);
            assertThat(dados.fcMedia()).isEqualTo(150);
            assertThat(dados.fcMax()).isEqualTo(175);
            assertThat(dados.tssCalculado()).isEqualTo(62);
            assertThat(dados.corrida()).isTrue();
            assertThat(dados.esporteDetectado()).isEqualTo("RUNNING");
            assertThat(dados.laps()).hasSize(2);
            assertThat(dados.laps().get(0).ordem()).isEqualTo(1);
            assertThat(dados.laps().get(0).distanciaKm()).isEqualTo(2.5);
            assertThat(dados.laps().get(1).fcMedia()).isEqualTo(152);
        }

        @Test
        @DisplayName("extrai elevação, potência e cadência (ppm de duas pernas) por lap e sessão")
        void extraiElevacaoPotenciaCadencia() throws IOException {
            byte[] fit = gerarFit(builder -> {
                SessionMesg session = new SessionMesg();
                session.setStartTime(new DateTime(Instant.parse("2026-07-01T10:00:00Z")));
                session.setSport(Sport.RUNNING);
                session.setTotalElapsedTime(1800f);
                session.setTotalDistance(5000f);
                session.setTotalAscent(65);
                session.setTotalDescent(57);
                session.setAvgPower(362);
                session.setAvgRunningCadence((short) 82);
                session.setAvgFractionalCadence(0.5f);
                builder.mesgs.add(session);

                LapMesg lap = new LapMesg();
                lap.setTotalElapsedTime(900f);
                lap.setTotalDistance(2500f);
                lap.setTotalAscent(4);
                lap.setTotalDescent(2);
                lap.setAvgPower(351);
                lap.setAvgRunningCadence((short) 80);
                lap.setAvgFractionalCadence(0.5f);
                builder.mesgs.add(lap);
            });

            FitSessionData dados = service.parse(new ByteArrayInputStream(fit));

            assertThat(dados.subidaMetros()).isEqualTo(65);
            assertThat(dados.descidaMetros()).isEqualTo(57);
            assertThat(dados.potenciaMediaWatts()).isEqualTo(362);
            // 82 passos de uma perna + 0.5 fracional → (82.5) * 2 = 165 ppm
            assertThat(dados.cadenciaMediaPpm()).isEqualTo(165);

            assertThat(dados.laps().get(0).subidaMetros()).isEqualTo(4);
            assertThat(dados.laps().get(0).descidaMetros()).isEqualTo(2);
            assertThat(dados.laps().get(0).potenciaMediaWatts()).isEqualTo(351);
            assertThat(dados.laps().get(0).cadenciaMediaPpm()).isEqualTo(161);
        }

        @Test
        @DisplayName("extrai running dynamics (GCT, equilíbrio, passada, oscilação, proporção, temperatura, tempo em movimento, calorias) com conversão de unidade")
        void extraiRunningDynamicsCompleto() throws IOException {
            byte[] fit = gerarFit(builder -> {
                SessionMesg session = new SessionMesg();
                session.setStartTime(new DateTime(Instant.parse("2026-07-01T10:00:00Z")));
                session.setSport(Sport.RUNNING);
                session.setTotalElapsedTime(1800f);
                session.setTotalTimerTime(1750f); // 30min elapsed, 29:10 em movimento
                session.setTotalCalories(650);
                session.setAvgStanceTime(252f);
                session.setAvgStanceTimeBalance(49.3f);
                session.setAvgStepLength(1050f); // mm -> 1.05 m
                session.setAvgVerticalOscillation(82f); // mm -> 8.2 cm
                session.setAvgVerticalRatio(6.8f);
                session.setAvgTemperature((byte) 22);
                builder.mesgs.add(session);

                LapMesg lap = new LapMesg();
                lap.setTotalElapsedTime(900f);
                lap.setTotalTimerTime(850f); // pausa de 50s dentro do lap
                lap.setAvgStanceTime(255f);
                lap.setAvgStanceTimeBalance(48.7f);
                lap.setAvgStepLength(980f); // mm -> 0.98 m
                lap.setAvgVerticalOscillation(90f); // mm -> 9.0 cm
                lap.setAvgVerticalRatio(7.1f);
                lap.setAvgTemperature((byte) 23);
                builder.mesgs.add(lap);
            });

            FitSessionData dados = service.parse(new ByteArrayInputStream(fit));

            assertThat(dados.tempoMovimento()).isEqualTo(java.time.Duration.ofSeconds(1750));
            assertThat(dados.calorias()).isEqualTo(650);
            assertThat(dados.gctMedioMs()).isEqualTo(252);
            assertThat(dados.gctEquilibrioPct()).isEqualByComparingTo("49.3");
            assertThat(dados.passadaMediaM()).isEqualByComparingTo("1.05");
            assertThat(dados.oscilacaoVerticalCm()).isEqualByComparingTo("8.2");
            assertThat(dados.proporcaoVerticalPct()).isEqualByComparingTo("6.8");
            assertThat(dados.temperaturaMediaC()).isEqualByComparingTo("22.0");

            var lap0 = dados.laps().get(0);
            assertThat(lap0.tempoMovimento()).isEqualTo(java.time.Duration.ofSeconds(850));
            assertThat(lap0.gctMedioMs()).isEqualTo(255);
            assertThat(lap0.gctEquilibrioPct()).isEqualByComparingTo("48.7");
            assertThat(lap0.passadaMediaM()).isEqualByComparingTo("0.98");
            assertThat(lap0.oscilacaoVerticalCm()).isEqualByComparingTo("9.0");
            assertThat(lap0.proporcaoVerticalPct()).isEqualByComparingTo("7.1");
            assertThat(lap0.temperaturaMediaC()).isEqualByComparingTo("23.0");
        }

        @Test
        @DisplayName("dispositivo sem running dynamics: campos ficam null; tempoMovimento ausente NÃO vira zero (diferente de duracao)")
        void semRunningDynamicsFicaNullSemFabricarZero() throws IOException {
            byte[] fit = gerarFit(builder -> {
                SessionMesg session = new SessionMesg();
                session.setStartTime(new DateTime(Instant.parse("2026-07-01T10:00:00Z")));
                session.setSport(Sport.RUNNING);
                session.setTotalElapsedTime(1800f);
                // sem totalTimerTime, sem running dynamics — relógio mais antigo
                builder.mesgs.add(session);

                LapMesg lap = new LapMesg();
                lap.setTotalElapsedTime(900f);
                builder.mesgs.add(lap);
            });

            FitSessionData dados = service.parse(new ByteArrayInputStream(fit));

            // duracao (totalElapsedTime) sempre presente — nunca null.
            assertThat(dados.duracao()).isEqualTo(java.time.Duration.ofMinutes(30));
            // tempoMovimento (totalTimerTime) ausente -> null, NUNCA Duration.ZERO fabricado.
            assertThat(dados.tempoMovimento()).isNull();
            assertThat(dados.calorias()).isNull();
            assertThat(dados.gctMedioMs()).isNull();
            assertThat(dados.gctEquilibrioPct()).isNull();
            assertThat(dados.passadaMediaM()).isNull();
            assertThat(dados.oscilacaoVerticalCm()).isNull();
            assertThat(dados.proporcaoVerticalPct()).isNull();
            assertThat(dados.temperaturaMediaC()).isNull();

            var lap0 = dados.laps().get(0);
            assertThat(lap0.duracao()).isEqualTo(java.time.Duration.ofMinutes(15));
            assertThat(lap0.tempoMovimento()).isNull();
            assertThat(lap0.gctMedioMs()).isNull();
        }

        @Test
        @DisplayName("esporte não-corrida não fabrica cadência de passos a partir de RPM (fica null)")
        void esporteNaoCorridaNaoConverteCadencia() throws IOException {
            byte[] fit = gerarFit(builder -> {
                SessionMesg session = new SessionMesg();
                session.setStartTime(new DateTime(Instant.parse("2026-07-01T10:00:00Z")));
                session.setSport(Sport.CYCLING);
                session.setTotalElapsedTime(3600f);
                session.setAvgCadence((short) 90); // RPM de pedal — dobrar viraria 180 "ppm" fantasma
                builder.mesgs.add(session);

                LapMesg lap = new LapMesg();
                lap.setTotalElapsedTime(1800f);
                lap.setAvgCadence((short) 90);
                builder.mesgs.add(lap);
            });

            FitSessionData dados = service.parse(new ByteArrayInputStream(fit));

            assertThat(dados.cadenciaMediaPpm()).isNull();
            assertThat(dados.laps().get(0).cadenciaMediaPpm()).isNull();
        }

        @Test
        @DisplayName("cadência sem fracional converte só o valor inteiro (duas pernas)")
        void cadenciaSemFracional() throws IOException {
            byte[] fit = gerarFit(builder -> {
                SessionMesg session = new SessionMesg();
                session.setStartTime(new DateTime(Instant.parse("2026-07-01T10:00:00Z")));
                session.setSport(Sport.RUNNING);
                session.setTotalElapsedTime(1800f);
                session.setAvgRunningCadence((short) 83);
                builder.mesgs.add(session);
            });

            FitSessionData dados = service.parse(new ByteArrayInputStream(fit));

            assertThat(dados.cadenciaMediaPpm()).isEqualTo(166);
        }

        @Test
        @DisplayName("lap e sessão sem elevação/potência/cadência (sem sensores) ficam null — nunca fabrica 0")
        void semSensoresFicaNull() throws IOException {
            byte[] fit = gerarFit(builder -> {
                SessionMesg session = new SessionMesg();
                session.setStartTime(new DateTime(Instant.parse("2026-07-01T10:00:00Z")));
                session.setSport(Sport.RUNNING);
                session.setTotalElapsedTime(1800f);
                builder.mesgs.add(session);

                LapMesg lap = new LapMesg();
                lap.setTotalElapsedTime(900f);
                builder.mesgs.add(lap);
            });

            FitSessionData dados = service.parse(new ByteArrayInputStream(fit));

            assertThat(dados.subidaMetros()).isNull();
            assertThat(dados.descidaMetros()).isNull();
            assertThat(dados.potenciaMediaWatts()).isNull();
            assertThat(dados.cadenciaMediaPpm()).isNull();
            assertThat(dados.laps().get(0).subidaMetros()).isNull();
            assertThat(dados.laps().get(0).descidaMetros()).isNull();
            assertThat(dados.laps().get(0).potenciaMediaWatts()).isNull();
            assertThat(dados.laps().get(0).cadenciaMediaPpm()).isNull();
        }

        @Test
        @DisplayName("esporte não-corrida é detectado como tal (corrida=false)")
        void esporteNaoCorrida() throws IOException {
            byte[] fit = gerarFit(builder -> {
                SessionMesg session = new SessionMesg();
                session.setStartTime(new DateTime(Instant.parse("2026-07-01T10:00:00Z")));
                session.setSport(Sport.CYCLING);
                session.setTotalElapsedTime(3600f);
                session.setTotalDistance(30000f);
                builder.mesgs.add(session);
            });

            FitSessionData dados = service.parse(new ByteArrayInputStream(fit));

            assertThat(dados.corrida()).isFalse();
            assertThat(dados.esporteDetectado()).isEqualTo("CYCLING");
        }

        @Test
        @DisplayName("dados parciais (sem GPS/FC — ex.: esteira) não falha, campos ausentes ficam null")
        void dadosParciaisSemFalhar() throws IOException {
            byte[] fit = gerarFit(builder -> {
                SessionMesg session = new SessionMesg();
                session.setStartTime(new DateTime(Instant.parse("2026-07-01T10:00:00Z")));
                session.setSport(Sport.RUNNING);
                session.setTotalElapsedTime(1200f);
                // sem distância, sem FC, sem TSS
                builder.mesgs.add(session);
            });

            FitSessionData dados = service.parse(new ByteArrayInputStream(fit));

            assertThat(dados.distanciaKm()).isNull();
            assertThat(dados.fcMedia()).isNull();
            assertThat(dados.fcMax()).isNull();
            assertThat(dados.tssCalculado()).isNull();
            assertThat(dados.duracao()).isEqualTo(java.time.Duration.ofMinutes(20));
        }

        @Test
        @DisplayName("lança FitParseException quando não há mensagem Session no arquivo")
        void semSessionLancaExcecao() throws IOException {
            byte[] fit = gerarFit(builder -> { /* só FileId, sem Session */ });

            assertThatThrownBy(() -> service.parse(new ByteArrayInputStream(fit)))
                    .isInstanceOf(FitParseException.class)
                    .hasMessageContaining("Session");
        }

        @Test
        @DisplayName("lança FitParseException quando a Session não tem startTime — nunca fabrica timestamp com now()")
        void semStartTimeLancaExcecao() throws IOException {
            byte[] fit = gerarFit(builder -> {
                SessionMesg session = new SessionMesg();
                // Sem setStartTime — cenário de dispositivo malformado/sem relógio sincronizado.
                session.setSport(Sport.RUNNING);
                session.setTotalElapsedTime(1800f);
                builder.mesgs.add(session);
            });

            assertThatThrownBy(() -> service.parse(new ByteArrayInputStream(fit)))
                    .isInstanceOf(FitParseException.class)
                    .hasMessageContaining("horário de início");
        }

        @Test
        @DisplayName("lança FitParseException para arquivo corrompido/não-FIT")
        void arquivoCorrompidoLancaExcecao() {
            InputStream lixo = new ByteArrayInputStream("isto não é um arquivo fit".getBytes());

            assertThatThrownBy(() -> service.parse(lixo))
                    .isInstanceOf(FitParseException.class);
        }

        @Test
        @DisplayName("lança FitParseException quando o arquivo tem múltiplas mensagens Session (multiesporte)")
        void multiplasSessionsLancaExcecao() throws IOException {
            byte[] fit = gerarFit(builder -> {
                SessionMesg natacao = new SessionMesg();
                natacao.setStartTime(new DateTime(Instant.parse("2026-07-01T10:00:00Z")));
                natacao.setSport(Sport.SWIMMING);
                natacao.setTotalElapsedTime(600f);
                builder.mesgs.add(natacao);

                SessionMesg corrida = new SessionMesg();
                corrida.setStartTime(new DateTime(Instant.parse("2026-07-01T10:15:00Z")));
                corrida.setSport(Sport.RUNNING);
                corrida.setTotalElapsedTime(1800f);
                builder.mesgs.add(corrida);
            });

            assertThatThrownBy(() -> service.parse(new ByteArrayInputStream(fit)))
                    .isInstanceOf(FitParseException.class)
                    .hasMessageContaining("múltiplas");
        }

        @Test
        @DisplayName("lança FitParseException quando o número de laps excede o limite (proteção contra flood)")
        void muitasLapsLancaExcecao() throws IOException {
            byte[] fit = gerarFit(builder -> {
                SessionMesg session = new SessionMesg();
                session.setStartTime(new DateTime(Instant.parse("2026-07-01T10:00:00Z")));
                session.setSport(Sport.RUNNING);
                session.setTotalElapsedTime(1800f);
                builder.mesgs.add(session);

                for (int i = 0; i < 1001; i++) {
                    LapMesg lap = new LapMesg();
                    lap.setTotalElapsedTime(1f);
                    builder.mesgs.add(lap);
                }
            });

            assertThatThrownBy(() -> service.parse(new ByteArrayInputStream(fit)))
                    .isInstanceOf(FitParseException.class)
                    .hasMessageContaining("laps");
        }
    }

    @Nested
    @DisplayName("fixture de referência (corrida 15 km, 16 laps — par do CSV do Garmin Connect)")
    class FixtureReferencia {

        @Test
        @DisplayName("parseia a fixture real com elevação, potência e cadência por lap e sessão")
        void parseiaFixtureReal() throws IOException {
            try (InputStream in = getClass().getResourceAsStream("/fit/corrida-15km-16laps.fit")) {
                assertThat(in).as("fixture /fit/corrida-15km-16laps.fit no classpath").isNotNull();
                FitSessionData dados = service.parse(in);

                assertThat(dados.laps()).hasSize(16);
                assertThat(dados.distanciaKm()).isCloseTo(15.0, org.assertj.core.data.Offset.offset(0.01));
                assertThat(dados.subidaMetros()).isEqualTo(65);
                assertThat(dados.descidaMetros()).isEqualTo(57);
                assertThat(dados.potenciaMediaWatts()).isEqualTo(362);
                assertThat(dados.cadenciaMediaPpm()).isEqualTo(165);
                // Amostras por lap conferidas contra o CSV (corrida-15km-16laps-garmin.csv)
                assertThat(dados.laps().get(0).subidaMetros()).isEqualTo(4);
                assertThat(dados.laps().get(0).potenciaMediaWatts()).isEqualTo(351);
                assertThat(dados.laps().get(0).cadenciaMediaPpm()).isEqualTo(161);
                assertThat(dados.laps().get(13).subidaMetros()).isEqualTo(11);
            }
        }
    }

    // ── Helper: gera um .fit real e mínimo via FileEncoder do próprio SDK ─────────────────────

    private interface FitBuilderCustomizer {
        void customize(FitBuilder builder) throws IOException;
    }

    private static class FitBuilder {
        final FileIdMesg fileId = new FileIdMesg();
        final java.util.List<com.garmin.fit.Mesg> mesgs = new java.util.ArrayList<>();

        FitBuilder() {
            fileId.setType(File.ACTIVITY);
            fileId.setManufacturer(1); // GARMIN
            fileId.setSerialNumber(1L);
            fileId.setTimeCreated(new DateTime(Instant.parse("2026-07-01T10:00:00Z")));
        }
    }

    private byte[] gerarFit(FitBuilderCustomizer customizer) throws IOException {
        FitBuilder builder = new FitBuilder();
        customizer.customize(builder);

        Path arquivo = tempDir.resolve("teste-" + System.nanoTime() + ".fit");
        FileEncoder encoder = new FileEncoder(arquivo.toFile());
        encoder.write(builder.fileId);
        for (com.garmin.fit.Mesg mesg : builder.mesgs) {
            encoder.write(mesg);
        }
        encoder.close();

        return Files.readAllBytes(arquivo);
    }

    @AfterEach
    void cleanup() {
        // @TempDir cuida da limpeza automaticamente
    }
}
