package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class IntervaladoElegibilidadeServiceTest {

    private IntervaladoElegibilidadeService service;

    private static final LocalDate DATA_REFERENCIA = LocalDate.of(2026, 2, 19);

    @BeforeEach
    void setUp() {
        service = new IntervaladoElegibilidadeService();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Atleta atletaSaudavel(NivelExperiencia nivel) {
        return Atleta.builder()
                .nome("Atleta Teste")
                .objetivo("Melhorar condicionamento")
                .nivelExperiencia(nivel)
                .temLesao(false)
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .build();
    }

    private PlanoMetaDados metaDadosPadrao(Double tsb, Double ctl, FasePeriodizacao fase) {
        return PlanoMetaDados.builder()
                .tsbAtual(tsb)
                // Gate fisiológico usa tsbProntidaoAtual (pré-treino).
                // Nos testes existentes, prontidão = tsbAtual (cenário simplificado).
                .tsbProntidaoAtual(tsb)
                .ctlAtual(ctl)
                .atlAtual(20.0)
                .rampRateAtual(5.0)
                .alertaDiasConsecutivos(false)
                .diasConsecutivosTreino(2)
                .fasePeriodizacao(fase)
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .build();
    }

    /**
     * Cria um TreinoRealizado mínimo para uso nos testes unitários.
     * Campos JPA nullable=false são ignorados pois não há contexto de persistência.
     */
    private TreinoRealizado treinoRealizado(TipoTreino tipo, LocalDate data, Integer rpe) {
        TreinoRealizado t = new TreinoRealizado();
        t.setTipoTreino(tipo);
        t.setDataTreino(data);
        t.setPercepcaoEsforco(rpe);
        t.setDistanciaKm(BigDecimal.valueOf(10));
        t.setDiaSemana(DiaSemana.SEGUNDA);
        t.setDuracaoMin(Duration.ofMinutes(60));
        t.setFcMedia(150);
        t.setFcMax(170);
        t.setPaceMedia(Duration.ofSeconds(330)); // ~5:30/km
        return t;
    }

    private TreinoRealizado treinoRealizadoComObs(TipoTreino tipo, LocalDate data, Integer rpe, String observacao) {
        TreinoRealizado t = treinoRealizado(tipo, data, rpe);
        t.setObservacao(observacao);
        return t;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PORTÃO 1 — Contraindicações absolutas
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar Elegivel quando atleta está em forma ideal (todos os portões passam)")
    void deveRetornarElegivelQuandoAtletaEmFormaIdeal() {
        Atleta atleta = atletaSaudavel(NivelExperiencia.AVANCADO);
        PlanoMetaDados meta = metaDadosPadrao(5.0, 45.0, FasePeriodizacao.BUILD);
        TreinoRealizado continuo = treinoRealizado(TipoTreino.CONTINUO, DATA_REFERENCIA.minusDays(2), 5);

        RecomendacaoIntervalado resultado = service.avaliar(atleta, meta, List.of(continuo), DATA_REFERENCIA);

        assertInstanceOf(RecomendacaoIntervalado.Elegivel.class, resultado);
        RecomendacaoIntervalado.Elegivel elegivel = (RecomendacaoIntervalado.Elegivel) resultado;
        assertNotNull(elegivel.categoria());
        assertTrue(elegivel.instrucaoParaLlm().contains("AUTORIZADO"));
    }

    @Test
    @DisplayName("Deve retornar Substituido com REGENERATIVO quando atleta tem lesão ativa")
    void deveRetornarSubstituidoQuandoAtletaTemLesaoAtiva() {
        Atleta atleta = Atleta.builder()
                .nome("Atleta Teste")
                .objetivo("Melhorar condicionamento")
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .temLesao(true)
                .descricaoLesao("Tendinite no joelho direito")
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .build();
        PlanoMetaDados meta = metaDadosPadrao(0.0, 30.0, FasePeriodizacao.BUILD);

        RecomendacaoIntervalado resultado = service.avaliar(atleta, meta, List.of(), DATA_REFERENCIA);

        assertInstanceOf(RecomendacaoIntervalado.Substituido.class, resultado);
        RecomendacaoIntervalado.Substituido sub = (RecomendacaoIntervalado.Substituido) resultado;
        assertEquals(TipoTreino.REGENERATIVO, sub.tipoFallback());
        assertTrue(sub.motivo().toLowerCase().contains("lesao"));
    }

    @Test
    @DisplayName("Deve retornar Substituido com REGENERATIVO quando TSB está extremamente negativo")
    void deveRetornarSubstituidoQuandoTsbExtremo() {
        Atleta atleta = atletaSaudavel(NivelExperiencia.AVANCADO);
        PlanoMetaDados meta = metaDadosPadrao(-35.0, 45.0, FasePeriodizacao.BUILD);

        RecomendacaoIntervalado resultado = service.avaliar(atleta, meta, List.of(), DATA_REFERENCIA);

        assertInstanceOf(RecomendacaoIntervalado.Substituido.class, resultado);
        RecomendacaoIntervalado.Substituido sub = (RecomendacaoIntervalado.Substituido) resultado;
        assertEquals(TipoTreino.REGENERATIVO, sub.tipoFallback());
        assertTrue(sub.motivo().contains("-35"));
    }

    @Test
    @DisplayName("Deve retornar Substituido com CONTINUO quando alerta de dias consecutivos está ativo")
    void deveRetornarSubstituidoQuandoDiasConsecutivosAlerta() {
        Atleta atleta = atletaSaudavel(NivelExperiencia.INTERMEDIARIO);
        PlanoMetaDados meta = PlanoMetaDados.builder()
                .tsbAtual(-5.0)
                .tsbProntidaoAtual(-5.0)
                .ctlAtual(30.0)
                .atlAtual(20.0)
                .rampRateAtual(5.0)
                .alertaDiasConsecutivos(true)
                .diasConsecutivosTreino(7)
                .fasePeriodizacao(FasePeriodizacao.BUILD)
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .build();

        RecomendacaoIntervalado resultado = service.avaliar(atleta, meta, List.of(), DATA_REFERENCIA);

        assertInstanceOf(RecomendacaoIntervalado.Substituido.class, resultado);
        RecomendacaoIntervalado.Substituido sub = (RecomendacaoIntervalado.Substituido) resultado;
        assertEquals(TipoTreino.CONTINUO, sub.tipoFallback());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PORTÃO 2 — Prontidão fisiológica
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar Degradado para Categoria D quando TSB está abaixo do limiar do nível")
    void deveRetornarDegradadoQuandoTsbAbaixoLimiarDoNivel() {
        // INTERMEDIARIO limiar = -15.0 | TSB = -20.0 → abaixo
        Atleta atleta = atletaSaudavel(NivelExperiencia.INTERMEDIARIO);
        PlanoMetaDados meta = metaDadosPadrao(-20.0, 30.0, FasePeriodizacao.BUILD);

        RecomendacaoIntervalado resultado = service.avaliar(atleta, meta, List.of(), DATA_REFERENCIA);

        assertInstanceOf(RecomendacaoIntervalado.Degradado.class, resultado);
        RecomendacaoIntervalado.Degradado deg = (RecomendacaoIntervalado.Degradado) resultado;
        assertEquals(CategoriaIntervalado.D, deg.categoriaSegura());
        assertTrue(deg.motivo().contains("TSB"));
    }

    @Test
    @DisplayName("Deve retornar Degradado para Categoria C quando RPE médio dos últimos 7 dias é elevado")
    void deveRetornarDegradadoQuandoRpeMediaElevada() {
        Atleta atleta = atletaSaudavel(NivelExperiencia.AVANCADO);
        // TSB e CTL bons para AVANCADO
        PlanoMetaDados meta = metaDadosPadrao(-5.0, 45.0, FasePeriodizacao.BUILD);

        // 3 treinos recentes com RPE 8, 8, 9 → média = 8.33 (>= 7.5)
        List<TreinoRealizado> treinos = List.of(
                treinoRealizado(TipoTreino.CONTINUO, DATA_REFERENCIA.minusDays(1), 8),
                treinoRealizado(TipoTreino.CONTINUO, DATA_REFERENCIA.minusDays(3), 8),
                treinoRealizado(TipoTreino.CONTINUO, DATA_REFERENCIA.minusDays(5), 9)
        );

        RecomendacaoIntervalado resultado = service.avaliar(atleta, meta, treinos, DATA_REFERENCIA);

        assertInstanceOf(RecomendacaoIntervalado.Degradado.class, resultado);
        RecomendacaoIntervalado.Degradado deg = (RecomendacaoIntervalado.Degradado) resultado;
        assertEquals(CategoriaIntervalado.C, deg.categoriaSegura());
        assertTrue(deg.motivo().toLowerCase().contains("rpe"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PORTÃO 3 — Recuperação desde último intensivo
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar Degradado quando recuperação desde último intensivo é insuficiente para INTERMEDIARIO")
    void deveRetornarDegradadoQuandoRecuperacaoInsuficiente() {
        Atleta atleta = atletaSaudavel(NivelExperiencia.INTERMEDIARIO);
        // TSB e CTL bons, RPE ok
        PlanoMetaDados meta = metaDadosPadrao(-5.0, 30.0, FasePeriodizacao.BUILD);

        // INTERMEDIARIO precisa de 60h. minusDays(1) = 24h < 60h → bloqueia
        TreinoRealizado intervalo = treinoRealizado(TipoTreino.INTERVALADO,
                DATA_REFERENCIA.minusDays(1), 7);

        RecomendacaoIntervalado resultado = service.avaliar(atleta, meta,
                List.of(intervalo), DATA_REFERENCIA);

        assertInstanceOf(RecomendacaoIntervalado.Degradado.class, resultado);
        RecomendacaoIntervalado.Degradado deg = (RecomendacaoIntervalado.Degradado) resultado;
        assertEquals(CategoriaIntervalado.D, deg.categoriaSegura());
        assertTrue(deg.motivo().toLowerCase().contains("recuperacao") ||
                   deg.motivo().toLowerCase().contains("intensivo"));
    }

    @Test
    @DisplayName("Deve respeitar janela de 72h para INICIANTE ao verificar recuperação")
    void deveRespeitar72HorasParaIniciante() {
        Atleta atleta = atletaSaudavel(NivelExperiencia.INICIANTE);
        PlanoMetaDados meta = metaDadosPadrao(-5.0, 18.0, FasePeriodizacao.BASE);

        // INICIANTE precisa 72h. minusDays(2) = 48h < 72h → bloqueia
        TreinoRealizado intervalo = treinoRealizado(TipoTreino.INTERVALADO,
                DATA_REFERENCIA.minusDays(2), 6);

        RecomendacaoIntervalado resultado = service.avaliar(atleta, meta,
                List.of(intervalo), DATA_REFERENCIA);

        assertInstanceOf(RecomendacaoIntervalado.Degradado.class, resultado);
        assertEquals(CategoriaIntervalado.D,
                ((RecomendacaoIntervalado.Degradado) resultado).categoriaSegura());
    }

    @Test
    @DisplayName("Deve permitir intervalado para AVANCADO após 72h de recuperação (acima do mínimo de 48h)")
    void devePermitir72HorasParaAvancado() {
        Atleta atleta = atletaSaudavel(NivelExperiencia.AVANCADO);
        // TSB bom, CTL bom, RPE baixo
        PlanoMetaDados meta = metaDadosPadrao(-5.0, 45.0, FasePeriodizacao.BUILD);

        // minusDays(3) = 72h > 48h mínimo para AVANCADO → portão 3 passa
        TreinoRealizado intervalo = treinoRealizado(TipoTreino.INTERVALADO,
                DATA_REFERENCIA.minusDays(3), 6);

        RecomendacaoIntervalado resultado = service.avaliar(atleta, meta,
                List.of(intervalo), DATA_REFERENCIA);

        assertInstanceOf(RecomendacaoIntervalado.Elegivel.class, resultado);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PORTÃO 4 — Base aeróbica mínima
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar Degradado quando CTL está abaixo do mínimo para INICIANTE")
    void deveRetornarDegradadoQuandoCtlInsuficiente() {
        Atleta atleta = atletaSaudavel(NivelExperiencia.INICIANTE);
        // CTL=10 < 15.0 (mínimo para INICIANTE)
        PlanoMetaDados meta = metaDadosPadrao(-5.0, 10.0, FasePeriodizacao.BASE);

        RecomendacaoIntervalado resultado = service.avaliar(atleta, meta, List.of(), DATA_REFERENCIA);

        assertInstanceOf(RecomendacaoIntervalado.Degradado.class, resultado);
        RecomendacaoIntervalado.Degradado deg = (RecomendacaoIntervalado.Degradado) resultado;
        assertEquals(CategoriaIntervalado.D, deg.categoriaSegura());
        assertTrue(deg.motivo().contains("CTL"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PORTÃO 5 — Seleção de categoria
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve selecionar Categoria B em fase BASE quando histórico indica última categoria foi A")
    void deveSelecionarCategoriaBEmFaseBaseComHistoricoA() {
        Atleta atleta = atletaSaudavel(NivelExperiencia.AVANCADO);
        PlanoMetaDados meta = metaDadosPadrao(5.0, 50.0, FasePeriodizacao.BASE);

        // Treino com observação indicando Categoria A (tiros curtos/400m)
        TreinoRealizado ultimoCatA = treinoRealizadoComObs(
                TipoTreino.INTERVALADO, DATA_REFERENCIA.minusDays(7), 7,
                "400m tiros curtos VO2max curto Z5");

        RecomendacaoIntervalado resultado = service.avaliar(atleta, meta,
                List.of(ultimoCatA), DATA_REFERENCIA);

        assertInstanceOf(RecomendacaoIntervalado.Elegivel.class, resultado);
        RecomendacaoIntervalado.Elegivel el = (RecomendacaoIntervalado.Elegivel) resultado;
        assertEquals(CategoriaIntervalado.B, el.categoria());
    }

    @Test
    @DisplayName("Deve selecionar Categoria D na fase TAPER independente do histórico")
    void deveSelecionarCategoriaDNaFaseTaper() {
        Atleta atleta = atletaSaudavel(NivelExperiencia.ELITE);
        PlanoMetaDados meta = metaDadosPadrao(8.0, 70.0, FasePeriodizacao.TAPER);

        RecomendacaoIntervalado resultado = service.avaliar(atleta, meta, List.of(), DATA_REFERENCIA);

        assertInstanceOf(RecomendacaoIntervalado.Elegivel.class, resultado);
        RecomendacaoIntervalado.Elegivel el = (RecomendacaoIntervalado.Elegivel) resultado;
        assertEquals(CategoriaIntervalado.D, el.categoria());
    }

    @Test
    @DisplayName("Deve selecionar Categoria D na fase SEMANA_PROVA para qualquer nível")
    void deveSelecionarCategoriaDNaFaseSemanaProva() {
        Atleta atleta = atletaSaudavel(NivelExperiencia.INTERMEDIARIO);
        PlanoMetaDados meta = metaDadosPadrao(10.0, 35.0, FasePeriodizacao.SEMANA_PROVA);

        RecomendacaoIntervalado resultado = service.avaliar(atleta, meta, List.of(), DATA_REFERENCIA);

        assertInstanceOf(RecomendacaoIntervalado.Elegivel.class, resultado);
        assertEquals(CategoriaIntervalado.D,
                ((RecomendacaoIntervalado.Elegivel) resultado).categoria());
    }

    @Test
    @DisplayName("Deve selecionar Categoria A quando histórico está vazio em fase DESENVOLVIMENTO_GERAL")
    void deveSelecionarCategoriaAComHistoricoVazio() {
        Atleta atleta = atletaSaudavel(NivelExperiencia.INTERMEDIARIO);
        PlanoMetaDados meta = metaDadosPadrao(0.0, 30.0, FasePeriodizacao.DESENVOLVIMENTO_GERAL);

        RecomendacaoIntervalado resultado = service.avaliar(atleta, meta, List.of(), DATA_REFERENCIA);

        assertInstanceOf(RecomendacaoIntervalado.Elegivel.class, resultado);
        assertEquals(CategoriaIntervalado.A,
                ((RecomendacaoIntervalado.Elegivel) resultado).categoria());
    }
}
