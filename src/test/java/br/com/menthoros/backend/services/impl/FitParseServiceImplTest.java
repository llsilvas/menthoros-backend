package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.fit.FitSessionData;
import br.com.menthoros.backend.exception.FitParseException;
import com.garmin.fit.DateTime;
import com.garmin.fit.File;
import com.garmin.fit.FileEncoder;
import com.garmin.fit.FileIdMesg;
import com.garmin.fit.LapMesg;
import com.garmin.fit.RecordMesg;
import com.garmin.fit.SessionMesg;
import com.garmin.fit.Sport;
import com.garmin.fit.SubSport;
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

        @Test
        @DisplayName("lê velocidade média e máxima e temperaturas mínima e máxima da sessão")
        void leVelocidadeETemperaturasDaSessao() throws IOException {
            try (InputStream in = getClass().getResourceAsStream("/fit/corrida-15km-16laps.fit")) {
                FitSessionData dados = service.parse(in);

                // 3,387 m/s no enhanced_max_speed (sonda da task 0.1)
                assertThat(dados.velocidadeMaxKmh()).isEqualByComparingTo("12.19");
                // 15 km / 5.612,6 s de tempo em movimento
                assertThat(dados.velocidadeMediaKmh()).isCloseTo(9.62, org.assertj.core.data.Offset.offset(0.01));
                assertThat(dados.temperaturaMinC()).isEqualByComparingTo("19.0");
                assertThat(dados.temperaturaMaxC()).isEqualByComparingTo("27.0");
            }
        }
    }

    @Nested
    @DisplayName("amostras de FC para o tempo em zonas (coach-ver-treino-realizado, D2/D6)")
    class AmostrasFc {

        @Test
        @DisplayName("fixture real: 5.619 amostras e soma das zonas = 5.639 s pelas regras do D6 (task 0.1)")
        void fixtureRealSomaPelasRegras() throws IOException {
            try (InputStream in = getClass().getResourceAsStream("/fit/corrida-15km-16laps.fit")) {
                FitSessionData dados = service.parse(in);

                assertThat(dados.amostrasFc()).hasSize(5619).allSatisfy(a -> assertThat(a.fc()).isNotNull());
                var zonas = new br.com.menthoros.backend.services.helper.ZonaTreinoService().calcularZonasFC(185, 165);
                var tempo = br.com.menthoros.backend.services.helper.TempoEmZonasCalculator.calcular(
                        dados.amostrasFc(), zonas, dados.tempoMovimento().toSeconds());
                assertThat(tempo.zonas().stream().mapToInt(z -> z.segundos()).sum()).isEqualTo(5639);
            }
        }

        @Test
        @DisplayName("record sem FC vira amostra com fc nula, sem quebrar o import")
        void recordSemFc() throws IOException {
            byte[] fit = gerarFit(builder -> {
                builder.mesgs.add(record(0, (short) 140));
                builder.mesgs.add(record(1, null));
                builder.mesgs.add(sessaoMinima());
            });

            FitSessionData dados = service.parse(new ByteArrayInputStream(fit));

            assertThat(dados.amostrasFc()).extracting(a -> a.fc()).containsExactly(140, null);
        }

        @Test
        @DisplayName("acima do limite de amostras o import segue, sem amostras (não recusa ultra legítimo)")
        void acimaDoLimiteDescartaAmostras() throws IOException {
            byte[] fit = gerarFit(builder -> {
                for (int i = 0; i <= FitParseServiceImpl.MAX_AMOSTRAS; i++) builder.mesgs.add(record(i, (short) 140));
                builder.mesgs.add(sessaoMinima());
            });

            FitSessionData dados = service.parse(new ByteArrayInputStream(fit));

            assertThat(dados.amostrasFc()).isEmpty();
        }

        private RecordMesg record(long segundos, Short fc) {
            RecordMesg r = new RecordMesg();
            r.setTimestamp(new DateTime(Instant.parse("2026-07-01T10:00:00Z").plusSeconds(segundos)));
            if (fc != null) r.setHeartRate(fc);
            return r;
        }

        private SessionMesg sessaoMinima() {
            SessionMesg session = new SessionMesg();
            session.setStartTime(new DateTime(Instant.parse("2026-07-01T10:00:00Z")));
            session.setSport(Sport.RUNNING);
            session.setTotalElapsedTime(60f);
            return session;
        }
    }

    @Nested
    @DisplayName("trajeto GPS e modelo do dispositivo (add-activity-gps-trace)")
    class TrajetoGps {

        private static final double DEG = 180.0 / 2_147_483_648.0;
        private static final int LAT0 = (int) Math.round(-23.55 / DEG);
        private static final int LNG0 = (int) Math.round(-46.63 / DEG);
        private static final int ONE_METER_LNG = 117;

        @Test
        @DisplayName("records com GPS viram pontos decimados a 5 m, com FC e timestamp")
        void extraiPontosGpsDecimados() throws IOException {
            byte[] fit = gerarFit(builder -> {
                for (int i = 0; i <= 10; i++) {
                    builder.mesgs.add(recordGps(i, LAT0, LNG0 + i * ONE_METER_LNG, (short) (140 + i), (float) i));
                }
                builder.mesgs.add(sessaoMinima());
            });

            FitSessionData dados = service.parse(new ByteArrayInputStream(fit));

            assertThat(dados.gpsSamples()).extracting(p -> p.timestampSeg() - inicioEpoch())
                    .containsExactly(0L, 5L, 10L);
            assertThat(dados.gpsSamples().get(0).lat()).isCloseTo(-23.55, org.assertj.core.data.Offset.offset(1e-6));
            assertThat(dados.gpsSamples().get(1).fc()).isEqualTo(145);
            // As amostras de FC continuam íntegras (uma por record), independentes da decimação.
            assertThat(dados.amostrasFc()).hasSize(11);
        }

        @Test
        @DisplayName("esteira (records sem posição) → lista vazia, import segue")
        void semGpsListaVazia() throws IOException {
            byte[] fit = gerarFit(builder -> {
                builder.mesgs.add(recordGps(0, null, null, (short) 140, 0f));
                builder.mesgs.add(recordGps(1, null, null, (short) 141, 10f));
                builder.mesgs.add(sessaoMinima());
            });

            FitSessionData dados = service.parse(new ByteArrayInputStream(fit));

            assertThat(dados.gpsSamples()).isEmpty();
            assertThat(dados.amostrasFc()).hasSize(2);
        }

        @Test
        @DisplayName("coordenada inválida (0x7FFFFFFF) é pulada sem derrubar a ingestão")
        void coordenadaInvalidaPulada() throws IOException {
            byte[] fit = gerarFit(builder -> {
                builder.mesgs.add(recordGps(0, LAT0, LNG0, null, 0f));
                builder.mesgs.add(recordGps(1, Integer.MAX_VALUE, LNG0, null, 10f));
                builder.mesgs.add(recordGps(2, LAT0, LNG0 + 20 * ONE_METER_LNG, null, 20f));
                builder.mesgs.add(sessaoMinima());
            });

            FitSessionData dados = service.parse(new ByteArrayInputStream(fit));

            assertThat(dados.gpsSamples()).extracting(p -> p.timestampSeg() - inicioEpoch()).containsExactly(0L, 2L);
        }

        @Test
        @DisplayName("deviceName vem do DeviceInfo.productName, com prioridade sobre o FileId")
        void deviceNameDoDeviceInfo() throws IOException {
            byte[] fit = gerarFit(builder -> {
                builder.fileId.setProductName("FR265");
                com.garmin.fit.DeviceInfoMesg info = new com.garmin.fit.DeviceInfoMesg();
                info.setDeviceIndex((short) 0);
                info.setManufacturer(1);
                info.setProductName("Forerunner 265");
                builder.mesgs.add(info);
                builder.mesgs.add(sessaoMinima());
            });

            FitSessionData dados = service.parse(new ByteArrayInputStream(fit));

            assertThat(dados.deviceName()).isEqualTo("Forerunner 265");
        }

        @Test
        @DisplayName("sem DeviceInfo, usa o productName do FileId; sem nenhum, fica nulo")
        void deviceNameFallbackENulo() throws IOException {
            byte[] comFileId = gerarFit(builder -> {
                builder.fileId.setProductName("FR265");
                builder.mesgs.add(sessaoMinima());
            });
            byte[] semNada = gerarFit(builder -> builder.mesgs.add(sessaoMinima()));

            assertThat(service.parse(new ByteArrayInputStream(comFileId)).deviceName()).isEqualTo("FR265");
            assertThat(service.parse(new ByteArrayInputStream(semNada)).deviceName()).isNull();
        }

        @Test
        @DisplayName("fixture real de 15 km traz trajeto decimado dentro do teto")
        void fixtureRealTemTrajeto() throws IOException {
            try (InputStream in = getClass().getResourceAsStream("/fit/corrida-15km-16laps.fit")) {
                FitSessionData dados = service.parse(in);

                assertThat(dados.gpsSamples()).isNotEmpty()
                        .hasSizeLessThanOrEqualTo(br.com.menthoros.backend.services.helper.GpsSampleCollector.MAX_POINTS);
                assertThat(dados.gpsSamples()).allSatisfy(p -> {
                    assertThat(p.lat()).isBetween(-90.0, 90.0);
                    assertThat(p.lng()).isBetween(-180.0, 180.0);
                });
            }
        }

        private long inicioEpoch() {
            return new DateTime(Instant.parse("2026-07-01T10:00:00Z")).getTimestamp();
        }

        private RecordMesg recordGps(long segundos, Integer lat, Integer lng, Short fc, Float distanciaM) {
            RecordMesg r = new RecordMesg();
            r.setTimestamp(new DateTime(Instant.parse("2026-07-01T10:00:00Z").plusSeconds(segundos)));
            if (lat != null) r.setPositionLat(lat);
            if (lng != null) r.setPositionLong(lng);
            if (fc != null) r.setHeartRate(fc);
            if (distanciaM != null) r.setDistance(distanciaM);
            return r;
        }

        private SessionMesg sessaoMinima() {
            SessionMesg session = new SessionMesg();
            session.setStartTime(new DateTime(Instant.parse("2026-07-01T10:00:00Z")));
            session.setSport(Sport.RUNNING);
            session.setTotalElapsedTime(60f);
            return session;
        }
    }

    @Nested
    @DisplayName("velocidade e temperatura da sessão (coach-ver-treino-realizado, D2)")
    class VelocidadeETemperatura {

        @Test
        @DisplayName("sem enhanced_max_speed usa max_speed")
        void usaMaxSpeedSemEnhanced() throws IOException {
            byte[] fit = gerarFit(builder -> {
                SessionMesg session = sessaoCorrida();
                session.setMaxSpeed(4.0f);
                builder.mesgs.add(session);
            });

            FitSessionData dados = service.parse(new ByteArrayInputStream(fit));

            assertThat(dados.velocidadeMaxKmh()).isEqualByComparingTo("14.40");
        }

        @Test
        @DisplayName("velocidade média usa tempo em movimento, não elapsed")
        void velocidadeMediaUsaTempoEmMovimento() throws IOException {
            byte[] fit = gerarFit(builder -> {
                SessionMesg session = sessaoCorrida();
                session.setTotalDistance(10000f);
                session.setTotalTimerTime(3000f);
                builder.mesgs.add(session);
            });

            FitSessionData dados = service.parse(new ByteArrayInputStream(fit));

            assertThat(dados.velocidadeMediaKmh()).isEqualTo(12.0);
        }

        @Test
        @DisplayName("Session sem os campos: tudo nulo e a importação não falha")
        void semCamposFicaNulo() throws IOException {
            byte[] fit = gerarFit(builder -> builder.mesgs.add(sessaoCorrida()));

            FitSessionData dados = service.parse(new ByteArrayInputStream(fit));

            assertThat(dados.velocidadeMediaKmh()).isNull();
            assertThat(dados.velocidadeMaxKmh()).isNull();
            assertThat(dados.temperaturaMinC()).isNull();
            assertThat(dados.temperaturaMaxC()).isNull();
        }

        private SessionMesg sessaoCorrida() {
            SessionMesg session = new SessionMesg();
            session.setStartTime(new DateTime(Instant.parse("2026-07-01T10:00:00Z")));
            session.setSport(Sport.RUNNING);
            session.setTotalElapsedTime(3600f);
            return session;
        }
    }

    // ── Helper: gera um .fit real e mínimo via FileEncoder do próprio SDK ─────────────────────

    @Nested
    @DisplayName("esteira (add-activity-treadmill-flag)")
    class Esteira {

        @Test
        @DisplayName("subSport TREADMILL marca esteira")
        void treadmillMarcaEsteira() throws IOException {
            assertThat(parseComSubSport(SubSport.TREADMILL).esteira()).isTrue();
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(value = SubSport.class,
                names = {"INDOOR_RUNNING", "GENERIC", "STREET", "TRAIL", "TRACK"})
        @DisplayName("outros subSport não marcam esteira")
        void outrosSubSportNaoMarcam(SubSport subSport) throws IOException {
            assertThat(parseComSubSport(subSport).esteira()).isFalse();
        }

        @Test
        @DisplayName("sem subSport não marca esteira")
        void semSubSportNaoMarca() throws IOException {
            assertThat(parseComSubSport(null).esteira()).isFalse();
        }

        @Test
        @DisplayName("fixture real outdoor não marca esteira")
        void fixtureOutdoorNaoMarca() throws IOException {
            try (InputStream in = getClass().getResourceAsStream("/fit/corrida-15km-16laps.fit")) {
                assertThat(service.parse(in).esteira()).isFalse();
            }
        }

        private FitSessionData parseComSubSport(SubSport subSport) throws IOException {
            byte[] fit = gerarFit(builder -> {
                SessionMesg session = new SessionMesg();
                session.setStartTime(new DateTime(Instant.parse("2026-07-01T10:00:00Z")));
                session.setSport(Sport.RUNNING);
                if (subSport != null) {
                    session.setSubSport(subSport);
                }
                session.setTotalElapsedTime(1800f);
                session.setTotalDistance(5000f);
                builder.mesgs.add(session);
            });
            return service.parse(new ByteArrayInputStream(fit));
        }
    }

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
