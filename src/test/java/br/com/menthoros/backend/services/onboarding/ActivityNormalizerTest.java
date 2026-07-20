package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.services.onboarding.impl.ActivityNormalizerImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityNormalizerTest {

    private final ActivityNormalizer normalizer = new ActivityNormalizerImpl();

    @Nested
    @DisplayName("toCanonical")
    class ToCanonical {

        @Test
        @DisplayName("arredonda distanciaKm para 2 casas decimais")
        void distanciaKmArredondaPara2Casas() {
            TreinoRealizado treino = treinoBase();

            NormalizedActivity resultado = normalizer.toCanonical(treino);

            assertThat(resultado.distanceKm()).isEqualTo(10.12);
        }

        @Test
        @DisplayName("averagePower e null quando ausente, nunca zero")
        void averagePowerNullQuandoAusente() {
            TreinoRealizado treino = treinoBase();
            treino.setPotenciaMedia(null);

            NormalizedActivity resultado = normalizer.toCanonical(treino);

            assertThat(resultado.averagePower()).isNull();
        }

        @Test
        @DisplayName("averagePower preserva valor quando presente")
        void averagePowerPreservaValor() {
            TreinoRealizado treino = treinoBase();
            treino.setPotenciaMedia(250);

            NormalizedActivity resultado = normalizer.toCanonical(treino);

            assertThat(resultado.averagePower()).isEqualTo(250);
        }

        @Test
        @DisplayName("rpe e null quando fonte nao fornece, nunca estimado de FC")
        void rpeNullQuandoFonteNaoFornece() {
            TreinoRealizado treino = treinoBase();
            treino.setPercepcaoEsforco(null);
            treino.setFcMedia(150); // presenca de FC nao deve gerar RPE estimado

            NormalizedActivity resultado = normalizer.toCanonical(treino);

            assertThat(resultado.rpe()).isNull();
        }

        @Test
        @DisplayName("rpe preserva valor quando fonte fornece")
        void rpePreservaValorQuandoFonteFornece() {
            TreinoRealizado treino = treinoBase();
            treino.setPercepcaoEsforco(7);

            NormalizedActivity resultado = normalizer.toCanonical(treino);

            assertThat(resultado.rpe()).isEqualTo(7);
        }

        @Test
        @DisplayName("averagePace reflete paceMedia em mm:ss/km")
        void averagePaceReflitaPaceMedia() {
            TreinoRealizado treino = treinoBase();
            treino.setPaceMedia(Duration.ofSeconds(4 * 60 + 30)); // 4:30/km

            NormalizedActivity resultado = normalizer.toCanonical(treino);

            assertThat(resultado.averagePace()).isEqualTo(Duration.ofSeconds(270));
        }

        @Test
        @DisplayName("sport e RUNNING para treino ja filtrado na ingestao")
        void sportERunning() {
            TreinoRealizado treino = treinoBase();

            NormalizedActivity resultado = normalizer.toCanonical(treino);

            assertThat(resultado.sport()).isEqualTo(Sport.RUNNING);
        }

        @Test
        @DisplayName("source reflete fonteDados do treino")
        void sourceReflitaFonteDados() {
            TreinoRealizado treino = treinoBase();
            treino.setFonteDados(FonteDados.STRAVA);

            NormalizedActivity resultado = normalizer.toCanonical(treino);

            assertThat(resultado.source()).isEqualTo(FonteDados.STRAVA);
        }

        @Test
        @DisplayName("dataQuality alta quando todos os campos presentes, fonte confiavel e consistente")
        void dataQualityAltaQuandoCompletoConfiavelConsistente() {
            TreinoRealizado treino = treinoBase();
            treino.setFonteDados(FonteDados.GARMIN);
            treino.setFcMedia(150);
            treino.setFcMax(170);
            treino.setPaceMedia(Duration.ofSeconds(270));
            treino.setPotenciaMedia(250);
            treino.setPercepcaoEsforco(6);
            // distancia (10.12km) e duracao (45min) consistentes com pace 4:30/km (~45.5min)

            NormalizedActivity resultado = normalizer.toCanonical(treino);

            assertThat(resultado.dataQuality()).isGreaterThan(0.9);
        }

        @Test
        @DisplayName("dataQuality baixa quando poucos campos e fonte pouco confiavel")
        void dataQualityBaixaQuandoIncompletoEPoucoConfiavel() {
            TreinoRealizado treino = treinoBase();
            treino.setFonteDados(FonteDados.MANUAL);
            // sem FC, sem power, sem RPE, sem pace

            NormalizedActivity resultado = normalizer.toCanonical(treino);

            assertThat(resultado.dataQuality()).isLessThan(0.5);
        }

        @Test
        @DisplayName("lanca IllegalArgumentException quando treino e null")
        void lancaExcecaoQuandoTreinoNulo() {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> normalizer.toCanonical(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nao pode ser nulo");
        }
    }

    private TreinoRealizado treinoBase() {
        Atleta atleta = new Atleta();
        atleta.setId(UUID.randomUUID());

        TreinoRealizado treino = new TreinoRealizado();
        treino.setId(UUID.randomUUID());
        treino.setAtleta(atleta);
        treino.setDataTreino(LocalDate.of(2026, 7, 1));
        treino.setDuracaoMin(Duration.ofMinutes(45));
        treino.setDistanciaKm(new BigDecimal("10.1234"));
        treino.setFonteDados(FonteDados.GARMIN);
        treino.setExternalId("abc123");
        return treino;
    }
}
