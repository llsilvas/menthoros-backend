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
        @DisplayName("lança FitParseException para arquivo corrompido/não-FIT")
        void arquivoCorrompidoLancaExcecao() {
            InputStream lixo = new ByteArrayInputStream("isto não é um arquivo fit".getBytes());

            assertThatThrownBy(() -> service.parse(lixo))
                    .isInstanceOf(FitParseException.class);
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
