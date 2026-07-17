package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ConfiancaInferencia;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.TipoTreino;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("ThresholdInferenceService")
class ThresholdInferenceServiceTest {

    private ThresholdInferenceService service;
    private LocalDate hoje;

    @BeforeEach
    void setUp() {
        service = new ThresholdInferenceService();
        hoje = LocalDate.of(2026, 6, 22);
    }

    @Nested
    @DisplayName("inferirFcLimiar")
    class InferirFcLimiar {

        @Test
        @DisplayName("retorna vazio quando lista vazia")
        void retornaVazioQuandoListaVazia() {
            assertThat(service.inferirFcLimiar(List.of(), hoje)).isEmpty();
        }

        @Test
        @DisplayName("retorna vazio quando menos de MIN_AMOSTRAS")
        void retornaVazioQuandoMenosDeMinAmostras() {
            List<TreinoRealizado> treinos = List.of(
                    treinoCom(fcMedia(160), hoje.minusDays(5)),
                    treinoCom(fcMedia(155), hoje.minusDays(10))
            );
            assertThat(service.inferirFcLimiar(treinos, hoje)).isEmpty();
        }

        @Test
        @DisplayName("retorna BAIXA para 3 ou 4 amostras")
        void retornaBAIXAParaTresOuQuatroAmostras() {
            List<TreinoRealizado> treinos = treinosComFc(List.of(160, 158, 155), hoje);
            Optional<ThresholdEstimate<Integer>> resultado = service.inferirFcLimiar(treinos, hoje);
            assertThat(resultado).isPresent();
            assertThat(resultado.get().confianca()).isEqualTo(ConfiancaInferencia.BAIXA);
        }

        @Test
        @DisplayName("retorna MEDIA para 5 a 9 amostras")
        void retornaMEDIAPara5A9Amostras() {
            List<TreinoRealizado> treinos = treinosComFc(List.of(168, 165, 162, 160, 158), hoje);
            Optional<ThresholdEstimate<Integer>> resultado = service.inferirFcLimiar(treinos, hoje);
            assertThat(resultado).isPresent();
            assertThat(resultado.get().confianca()).isEqualTo(ConfiancaInferencia.MEDIA);
        }

        @Test
        @DisplayName("retorna ALTA para 10 ou mais amostras")
        void retornaALTAPara10OuMaisAmostras() {
            List<TreinoRealizado> treinos = treinosComFc(
                    List.of(170, 168, 165, 163, 162, 160, 158, 155, 153, 150), hoje);
            Optional<ThresholdEstimate<Integer>> resultado = service.inferirFcLimiar(treinos, hoje);
            assertThat(resultado).isPresent();
            assertThat(resultado.get().confianca()).isEqualTo(ConfiancaInferencia.ALTA);
        }

        @Test
        @DisplayName("calcula mediana do quintil superior — top 20% de 10 treinos")
        void calculaMedianaDoQuintilSuperior() {
            // 10 treinos, ordenados desc: [170, 168, 165, 163, 162, 160, 158, 155, 153, 150]
            // top 20% = ceil(10 * 0.20) = 2 elementos: [170, 168]
            // mediana conservadora (n par → n/2-1): [170, 168].get(0) = 170
            List<TreinoRealizado> treinos = treinosComFc(
                    List.of(170, 168, 165, 163, 162, 160, 158, 155, 153, 150), hoje);
            Optional<ThresholdEstimate<Integer>> resultado = service.inferirFcLimiar(treinos, hoje);
            assertThat(resultado).isPresent();
            assertThat(resultado.get().valor().intValue()).isEqualTo(170);
            assertThat(resultado.get().amostras()).isEqualTo(10);
        }

        @Test
        @DisplayName("ignora treinos com fcMedia nula")
        void ignoraTreinosComFcMediaNula() {
            List<TreinoRealizado> treinos = new ArrayList<>();
            treinos.add(treinoCom(null, hoje.minusDays(5)));
            treinos.add(treinoCom(null, hoje.minusDays(8)));
            treinos.addAll(treinosComFc(List.of(160, 158, 155), hoje));
            Optional<ThresholdEstimate<Integer>> resultado = service.inferirFcLimiar(treinos, hoje);
            assertThat(resultado).isPresent();
            assertThat(resultado.get().amostras()).isEqualTo(3);
        }

        @Test
        @DisplayName("ignora treinos com duração abaixo de 20 min")
        void ignoraTreinosComDuracaoAbaixoDe20Min() {
            TreinoRealizado curto = treinoCom(fcMedia(175), hoje.minusDays(2));
            curto.setDuracaoMin(Duration.ofMinutes(15));
            List<TreinoRealizado> treinos = new ArrayList<>();
            treinos.add(curto);
            treinos.addAll(treinosComFc(List.of(160, 158, 155), hoje));
            Optional<ThresholdEstimate<Integer>> resultado = service.inferirFcLimiar(treinos, hoje);
            assertThat(resultado).isPresent();
            assertThat(resultado.get().amostras()).isEqualTo(3);
        }

        @Test
        @DisplayName("ignora treinos fora da janela de 30 dias")
        void ignoraTreinosForaDaJanelaDe30Dias() {
            TreinoRealizado antigo = treinoCom(fcMedia(175), hoje.minusDays(31));
            List<TreinoRealizado> treinos = new ArrayList<>();
            treinos.add(antigo);
            treinos.addAll(treinosComFc(List.of(160, 158, 155), hoje));
            Optional<ThresholdEstimate<Integer>> resultado = service.inferirFcLimiar(treinos, hoje);
            assertThat(resultado).isPresent();
            assertThat(resultado.get().amostras()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("inferirPaceLimiar")
    class InferirPaceLimiar {

        @Test
        @DisplayName("retorna vazio quando lista vazia")
        void retornaVazioQuandoListaVazia() {
            assertThat(service.inferirPaceLimiar(List.of(), hoje)).isEmpty();
        }

        @Test
        @DisplayName("retorna vazio quando menos de MIN_AMOSTRAS")
        void retornaVazioQuandoMenosDeMinAmostras() {
            List<TreinoRealizado> treinos = List.of(
                    treinoContínuoComPace(285, hoje.minusDays(5)),
                    treinoContínuoComPace(300, hoje.minusDays(10))
            );
            assertThat(service.inferirPaceLimiar(treinos, hoje)).isEmpty();
        }

        @Test
        @DisplayName("ignora tipos não contínuos (INTERVALADO, TIRO)")
        void ignoraTiposNaoContinuos() {
            List<TreinoRealizado> treinos = new ArrayList<>();
            TreinoRealizado intervalado = treinoContínuoComPace(240, hoje.minusDays(3));
            intervalado.setTipoTreino(TipoTreino.INTERVALADO);
            treinos.add(intervalado);
            treinos.addAll(treinosComPace(List.of(285L, 295L, 305L), hoje));
            Optional<ThresholdEstimate<BigDecimal>> resultado = service.inferirPaceLimiar(treinos, hoje);
            assertThat(resultado).isPresent();
            assertThat(resultado.get().amostras()).isEqualTo(3);
        }

        @Test
        @DisplayName("calcula mediana do quintil mais rápido")
        void calculaMedianaDoQuintilMaisRapido() {
            // 5 treinos com paces crescentes [270, 280, 285, 295, 305] s/km
            // top 20% = ceil(5 * 0.20) = 1 elemento: [270]
            // mediana de 1 elemento = 270 s/km
            List<TreinoRealizado> treinos = treinosComPace(List.of(270L, 280L, 285L, 295L, 305L), hoje);
            Optional<ThresholdEstimate<BigDecimal>> resultado = service.inferirPaceLimiar(treinos, hoje);
            assertThat(resultado).isPresent();
            assertThat(resultado.get().valor().doubleValue()).isEqualTo(4.5); // 270/60 = 4.5
        }

        @Test
        @DisplayName("converte segundos para decimal de minutos (285s → 4.7500)")
        void converteSegundosParaDecimalMinutos() {
            List<TreinoRealizado> treinos = treinosComPace(List.of(285L, 290L, 295L), hoje);
            Optional<ThresholdEstimate<BigDecimal>> resultado = service.inferirPaceLimiar(treinos, hoje);
            assertThat(resultado).isPresent();
            // top 20% de 3 = ceil(0.6) = 1 → [285] → mediana = 285 → 285/60 = 4.75
            assertThat(resultado.get().valor().toString()).isEqualTo("4.7500");
        }

        @Test
        @DisplayName("retorna vazio quando paceMedia nulo em todos os filtrados")
        void retornaVazioQuandoPaceMediaNuloEmTodosFiltrados() {
            List<TreinoRealizado> treinos = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                TreinoRealizado t = treinoContínuoComPace(0, hoje.minusDays(i + 1));
                t.setPaceMedia(null);
                treinos.add(t);
            }
            assertThat(service.inferirPaceLimiar(treinos, hoje)).isEmpty();
        }
    }

    @Nested
    @DisplayName("resolverDistanciaMetros")
    class ResolverDistanciaMetros {

        @ParameterizedTest
        @DisplayName("resolve cada valor do enum DistanciaProva quando distanciaKm é nulo")
        @CsvSource({
                "KM_5, 5000",
                "KM_10, 10000",
                "KM_21, 21097",
                "KM_42, 42195"
        })
        void resolvePorEnumQuandoDistanciaKmNulo(DistanciaProva distancia, int metrosEsperados) {
            Prova prova = provaCom(distancia, null, LocalTime.of(0, 45, 0));
            assertThat(service.resolverDistanciaMetros(prova)).isEqualTo(metrosEsperados);
        }

        @Test
        @DisplayName("prioriza distanciaKm customizado sobre o enum quando ambos presentes")
        void priorizaDistanciaKmCustomizadoSobreEnum() {
            Prova prova = provaCom(DistanciaProva.KM_10, BigDecimal.valueOf(12.5), LocalTime.of(1, 0, 0));
            assertThat(service.resolverDistanciaMetros(prova)).isEqualTo(12500);
        }
    }

    @Nested
    @DisplayName("inferirPaceLimiarDeProva")
    class InferirPaceLimiarDeProva {

        @Test
        @DisplayName("10K exato — sem normalização, offset direto (278s/km → 4.6333 min/km)")
        void dez_k_exato_semNormalizacao() {
            Prova prova = provaCom(DistanciaProva.KM_10, null, LocalTime.of(0, 45, 0)); // 2700s
            BigDecimal resultado = service.inferirPaceLimiarDeProva(prova);
            assertThat(resultado.toString()).isEqualTo("4.6333");
        }

        @Test
        @DisplayName("5000m — normalização + offset")
        void cinco_mil_metros_normalizacaoEOffset() {
            Prova prova = provaCom(DistanciaProva.KM_5, null, LocalTime.of(0, 25, 0)); // 1500s
            BigDecimal resultado = service.inferirPaceLimiarDeProva(prova);
            assertThat(resultado.doubleValue()).isCloseTo(5.3457, within(0.02));
        }

        @Test
        @DisplayName("21097m — normalização + offset")
        void meia_maratona_normalizacaoEOffset() {
            Prova prova = provaCom(DistanciaProva.KM_21, null, LocalTime.of(1, 45, 0)); // 6300s
            BigDecimal resultado = service.inferirPaceLimiarDeProva(prova);
            assertThat(resultado.doubleValue()).isCloseTo(4.8925, within(0.02));
        }

        @Test
        @DisplayName("resultado é determinístico — mesma prova gera sempre o mesmo valor")
        void resultadoDeterministico() {
            Prova prova = provaCom(DistanciaProva.KM_21, null, LocalTime.of(1, 45, 0));
            BigDecimal primeira = service.inferirPaceLimiarDeProva(prova);
            BigDecimal segunda = service.inferirPaceLimiarDeProva(prova);
            assertThat(primeira).isEqualByComparingTo(segunda);
        }
    }

    @Nested
    @DisplayName("encontrarProvaValidaMaisRecente")
    class EncontrarProvaValidaMaisRecente {

        @Test
        @DisplayName("ignora prova fora da faixa (3K)")
        void ignoraProvaForaDaFaixa() {
            Prova provaCurta = provaCom(DistanciaProva.KM_5, BigDecimal.valueOf(3.0), LocalTime.of(0, 12, 0));
            assertThat(service.encontrarProvaValidaMaisRecente(List.of(provaCurta))).isEmpty();
        }

        @Test
        @DisplayName("considera válida prova de 21097m via enum KM_21 (distanciaKm nulo)")
        void considerValidaMeiaMaratonaViaEnum() {
            Prova meia = provaCom(DistanciaProva.KM_21, null, LocalTime.of(1, 45, 0));
            Optional<Prova> resultado = service.encontrarProvaValidaMaisRecente(List.of(meia));
            assertThat(resultado).contains(meia);
        }

        @Test
        @DisplayName("considera válida prova de 10K com distanciaKm customizado")
        void considerValidaDezKCustomizado() {
            Prova dezK = provaCom(DistanciaProva.KM_10, BigDecimal.valueOf(10.0), LocalTime.of(0, 45, 0));
            Optional<Prova> resultado = service.encontrarProvaValidaMaisRecente(List.of(dezK));
            assertThat(resultado).contains(dezK);
        }

        @Test
        @DisplayName("retorna vazio para lista vazia, sem erro")
        void retornaVazioParaListaVazia() {
            assertThat(service.encontrarProvaValidaMaisRecente(List.of())).isEmpty();
        }

        @Test
        @DisplayName("retorna a primeira prova válida da lista (já ordenada por data DESC)")
        void retornaPrimeiraProvaValidaDaLista() {
            Prova maisRecente = provaCom(DistanciaProva.KM_10, null, LocalTime.of(0, 45, 0));
            Prova maisAntiga = provaCom(DistanciaProva.KM_21, null, LocalTime.of(1, 45, 0));
            Optional<Prova> resultado = service.encontrarProvaValidaMaisRecente(List.of(maisRecente, maisAntiga));
            assertThat(resultado).contains(maisRecente);
        }
    }

    // ======================== HELPERS ========================

    private Prova provaCom(DistanciaProva distancia, BigDecimal distanciaKm, LocalTime tempoRealizado) {
        Prova prova = new Prova();
        prova.setDistancia(distancia);
        prova.setDistanciaKm(distanciaKm);
        prova.setTempoRealizado(tempoRealizado);
        return prova;
    }

    private TreinoRealizado treinoCom(Integer fc, LocalDate data) {
        TreinoRealizado t = new TreinoRealizado();
        t.setTipoTreino(TipoTreino.CONTINUO);
        t.setDiaSemana(DiaSemana.SEGUNDA);
        t.setDataTreino(data);
        t.setDuracaoMin(Duration.ofMinutes(45));
        t.setFcMedia(fc);
        return t;
    }

    private Integer fcMedia(int valor) {
        return valor;
    }

    private List<TreinoRealizado> treinosComFc(List<Integer> fcs, LocalDate hoje) {
        List<TreinoRealizado> lista = new ArrayList<>();
        for (int i = 0; i < fcs.size(); i++) {
            lista.add(treinoCom(fcs.get(i), hoje.minusDays(i + 1)));
        }
        return lista;
    }

    private TreinoRealizado treinoContínuoComPace(long segundos, LocalDate data) {
        TreinoRealizado t = new TreinoRealizado();
        t.setTipoTreino(TipoTreino.CONTINUO);
        t.setDiaSemana(DiaSemana.SEGUNDA);
        t.setDataTreino(data);
        t.setDuracaoMin(Duration.ofMinutes(45));
        if (segundos > 0) {
            t.setPaceMedia(Duration.ofSeconds(segundos));
        }
        return t;
    }

    private List<TreinoRealizado> treinosComPace(List<Long> paces, LocalDate hoje) {
        List<TreinoRealizado> lista = new ArrayList<>();
        for (int i = 0; i < paces.size(); i++) {
            lista.add(treinoContínuoComPace(paces.get(i), hoje.minusDays(i + 1)));
        }
        return lista;
    }
}
