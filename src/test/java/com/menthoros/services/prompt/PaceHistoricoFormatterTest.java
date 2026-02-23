package com.menthoros.services.prompt;

import com.menthoros.entity.Atleta;
import com.menthoros.entity.TreinoRealizado;
import com.menthoros.enums.NivelExperiencia;
import com.menthoros.enums.TipoTreino;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaceHistoricoFormatterTest {

    private PaceHistoricoFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new PaceHistoricoFormatter();
    }

    // ======================== formatarHistoricoPace ========================

    @Test
    @DisplayName("Deve agregar paceMedia por tipo de treino com min/média/max corretos")
    void deveAgregarPaceMediaPorTipoTreino() {
        // Dado: 5 treinos CONTINUO com paceMedia [5:20, 5:30, 5:40, 5:25, 5:35]
        // min = 5:20 (320 seg), avg = (320+330+340+325+335)/5 = 1650/5 = 330 seg = 5:30
        List<TreinoRealizado> treinos = List.of(
                criarTreino(TipoTreino.CONTINUO, 5, 20),
                criarTreino(TipoTreino.CONTINUO, 5, 30),
                criarTreino(TipoTreino.CONTINUO, 5, 40),
                criarTreino(TipoTreino.CONTINUO, 5, 25),
                criarTreino(TipoTreino.CONTINUO, 5, 35)
        );

        String resultado = formatter.formatarHistoricoPace(treinos);

        assertThat(resultado).contains("5:20/km");
        assertThat(resultado).contains("5:30/km");
        assertThat(resultado).contains("CONTINUO");
    }

    @Test
    @DisplayName("Deve exibir apenas os treinos passados na lista, sem filtro adicional por data")
    void deveExibirApenasOsTreinosPassados() {
        // Dado: somente 3 treinos (TreinoHistoricoProvider já filtrou a janela)
        List<TreinoRealizado> treinos = List.of(
                criarTreino(TipoTreino.LONGO, 5, 50),
                criarTreino(TipoTreino.LONGO, 6, 0),
                criarTreino(TipoTreino.LONGO, 5, 55)
        );

        String resultado = formatter.formatarHistoricoPace(treinos);

        // Contagem deve ser 3
        assertThat(resultado).contains("LONGO");
        assertThat(resultado).contains("| 3");
    }

    @Test
    @DisplayName("Deve gerar aviso de zona para tipos sem histórico recente")
    void deveGerarAvisoParaTipoSemHistorico() {
        // Dado: apenas treinos CONTINUO — sem INTERVALADO
        List<TreinoRealizado> treinos = List.of(
                criarTreino(TipoTreino.CONTINUO, 5, 30)
        );

        String resultado = formatter.formatarHistoricoPace(treinos);

        assertThat(resultado).doesNotContain("INTERVALADO");
        assertThat(resultado).contains("Tipos sem histórico recente");
        assertThat(resultado).contains("zona calculada");
    }

    @Test
    @DisplayName("Deve lidar com lista vazia retornando aviso de sem histórico")
    void deveLidarComListaVazia() {
        String resultado = formatter.formatarHistoricoPace(List.of());

        assertThat(resultado).contains("Sem histórico");
        assertThat(resultado).contains("zonas teóricas");
    }

    // ======================== calcularTetoPorTipo ========================

    @Test
    @DisplayName("Deve calcular teto 2% mais rápido que o melhor pace recente")
    void deveCalcularTetoPorTipo() {
        // Dado: INTERVALADO com melhor pace = 4:48/km = 288 seg
        // Teto = 288 seg → decimal = 4.8000 min/km → × 0.98 = 4.7040 ≈ 4:42/km
        List<TreinoRealizado> treinos = List.of(
                criarTreino(TipoTreino.INTERVALADO, 4, 48),  // melhor
                criarTreino(TipoTreino.INTERVALADO, 4, 50),
                criarTreino(TipoTreino.INTERVALADO, 4, 52)
        );

        Map<TipoTreino, BigDecimal> tetos = formatter.calcularTetoPorTipo(treinos);

        assertThat(tetos).containsKey(TipoTreino.INTERVALADO);
        BigDecimal teto = tetos.get(TipoTreino.INTERVALADO);
        // 4:48 = 4.8 min, × 0.98 = 4.704. Deve ser ≤ 4:43 = 4.7167 min
        assertThat(teto).isLessThanOrEqualTo(new BigDecimal("4.7167"));
        // E não deve ser mais rápido que 4:39 = 4.65 (evitar teto absurdo)
        assertThat(teto).isGreaterThanOrEqualTo(new BigDecimal("4.65"));
    }

    // ======================== verificarPaceLimiarAtualizado ========================

    @Test
    @DisplayName("Deve retornar aviso quando paceLimiar está desatualizado (> 90 dias)")
    void deveRetornarAvisoComPaceLimiarDesatualizado() {
        Atleta atleta = Atleta.builder()
                .nome("Test")
                .objetivo("Correr")
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .paceLimiar(new BigDecimal("5.00"))
                .dataUltimoTestePace(LocalDate.now().minusDays(100))
                .build();

        String aviso = formatter.verificarPaceLimiarAtualizado(atleta);

        assertThat(aviso).isNotEmpty();
        assertThat(aviso).contains("desatualizado");
        assertThat(aviso).contains("100");
    }

    @Test
    @DisplayName("Deve retornar string vazia quando paceLimiar está atualizado (< 90 dias)")
    void deveRetornarVazioComPaceLimiarAtualizado() {
        Atleta atleta = Atleta.builder()
                .nome("Test")
                .objetivo("Correr")
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .paceLimiar(new BigDecimal("5.00"))
                .dataUltimoTestePace(LocalDate.now().minusDays(30))
                .build();

        String aviso = formatter.verificarPaceLimiarAtualizado(atleta);

        assertThat(aviso).isEmpty();
    }

    // ======================== helpers ========================

    private TreinoRealizado criarTreino(TipoTreino tipo, int minutos, int segundos) {
        TreinoRealizado t = new TreinoRealizado();
        t.setTipoTreino(tipo);
        t.setPaceMedia(Duration.ofSeconds((long) minutos * 60 + segundos));
        return t;
    }
}
