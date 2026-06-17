package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FasePeriodizacao;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider.ContextoTreino;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Fixtures determinísticas de atletas para o golden-master de {@code buildOptimizedPrompt}.
 *
 * <p>Todas as datas são fixas e relativas a {@link #HOJE} para que o prompt seja reprodutível.
 * O teste congela {@code LocalDate.now()} em {@link #HOJE} no escopo do build, então os campos
 * dependentes de "hoje" (idade, dias até a prova, validade do teste de pace) ficam estáveis sem
 * alterar o código de produção.</p>
 */
final class PlanoPromptArquetipos {

    /** Data congelada como "hoje" durante o build (ver teste). */
    static final LocalDate HOJE = LocalDate.of(2026, 1, 15);
    /** Início da semana planejada (segunda-feira seguinte). */
    static final LocalDate INICIO_SEMANA = LocalDate.of(2026, 1, 19);

    private PlanoPromptArquetipos() {
    }

    /** Componentes de um arquétipo prontos para alimentar {@code buildOptimizedPrompt}. */
    record Arquetipo(
            String nome,
            Atleta atleta,
            PlanoMetaDados meta,
            Prova prova,
            ContextoTreino contexto,
            LocalDate inicioSemana,
            List<DiaSemana> diasEfetivos) {

        @Override
        public String toString() {
            return nome;
        }
    }

    static List<Arquetipo> todos() {
        return List.of(
                inicianteSemLesao(),
                avancadoTsbBaixo(),
                comLesaoAtiva(),
                taperSemanaProva(),
                semDados());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Arquétipos
    // ─────────────────────────────────────────────────────────────────────────

    private static Arquetipo inicianteSemLesao() {
        Atleta atleta = atletaBase()
                .nome("Ana Iniciante")
                .nivelExperiencia(NivelExperiencia.INICIANTE)
                .objetivo("Completar a primeira 10k")
                .fcMaxima(190).fcRepouso(65).fcLimiar(165)
                .paceLimiar(new BigDecimal("6.30"))
                .build();
        PlanoMetaDados meta = metaBase(atleta)
                .ctlAtual(20.0).atlAtual(18.0).tsbAtual(2.0).tsbProntidaoAtual(2.0)
                .rampRateAtual(2.5).fasePeriodizacao(FasePeriodizacao.BASE)
                .diasConsecutivosTreino(1).build();
        List<TreinoRealizado> treinos = historicoLeve();
        return new Arquetipo("iniciante-sem-lesao", atleta, meta, null,
                contexto(treinos), INICIO_SEMANA, diasUteis());
    }

    private static Arquetipo avancadoTsbBaixo() {
        Atleta atleta = atletaBase()
                .nome("Bruno Avancado")
                .nivelExperiencia(NivelExperiencia.AVANCADO)
                .objetivo("Sub-3h na maratona")
                .fcMaxima(186).fcRepouso(48).fcLimiar(172)
                .paceLimiar(new BigDecimal("3.50"))
                .build();
        // TSB bem negativo → degrada elegibilidade de intervalado
        PlanoMetaDados meta = metaBase(atleta)
                .ctlAtual(75.0).atlAtual(95.0).tsbAtual(-20.0).tsbProntidaoAtual(-18.0)
                .rampRateAtual(7.0).fasePeriodizacao(FasePeriodizacao.BUILD)
                .diasConsecutivosTreino(5).build();
        return new Arquetipo("avancado-tsb-baixo", atleta, meta, null,
                contexto(historicoPesado()), INICIO_SEMANA, diasSeisDias());
    }

    private static Arquetipo comLesaoAtiva() {
        Atleta atleta = atletaBase()
                .nome("Carla Lesionada")
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .objetivo("Voltar a correr sem dor")
                .fcMaxima(188).fcRepouso(58).fcLimiar(168)
                .paceLimiar(new BigDecimal("5.10"))
                .temLesao(true)
                .descricaoLesao("Tendinite no tendão de Aquiles direito")
                .build();
        PlanoMetaDados meta = metaBase(atleta)
                .ctlAtual(40.0).atlAtual(35.0).tsbAtual(5.0).tsbProntidaoAtual(5.0)
                .rampRateAtual(1.0).fasePeriodizacao(FasePeriodizacao.BASE)
                .diasConsecutivosTreino(0).build();
        return new Arquetipo("com-lesao-ativa", atleta, meta, null,
                contexto(historicoLeve()), INICIO_SEMANA, diasUteis());
    }

    private static Arquetipo taperSemanaProva() {
        Atleta atleta = atletaBase()
                .nome("Diego Taper")
                .nivelExperiencia(NivelExperiencia.AVANCADO)
                .objetivo("PR nos 21k")
                .fcMaxima(185).fcRepouso(50).fcLimiar(171)
                .paceLimiar(new BigDecimal("4.05"))
                .build();
        PlanoMetaDados meta = metaBase(atleta)
                .ctlAtual(70.0).atlAtual(55.0).tsbAtual(15.0).tsbProntidaoAtual(15.0)
                .rampRateAtual(-3.0).fasePeriodizacao(FasePeriodizacao.SEMANA_PROVA)
                .diasConsecutivosTreino(2).build();
        // Prova-alvo dentro da semana planejada
        Prova prova = new Prova();
        prova.setNomeProva("Meia Maratona da Cidade");
        prova.setDataProva(LocalDate.of(2026, 1, 25));
        prova.setDistanciaKm(new BigDecimal("21.097"));
        prova.setPaceObjetivo(new BigDecimal("4.10"));
        prova.setTempoObjetivo(LocalTime.of(1, 28, 0));
        prova.setTsbIdealProva(20.0);
        prova.setProvaAlvo(true);
        return new Arquetipo("taper-semana-prova", atleta, meta, prova,
                contexto(historicoModerado()), INICIO_SEMANA, diasTaper());
    }

    private static Arquetipo semDados() {
        // Exercita os fallbacks: métricas ausentes, sem histórico, sem prova.
        Atleta atleta = Atleta.builder()
                .nome("Eva SemDados")
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .objetivo("Melhorar condicionamento")
                .diasDisponiveis(diasUteis())
                .diaPreferidoLongo(DiaSemana.SABADO)
                .temLesao(false)
                .build();
        PlanoMetaDados meta = PlanoMetaDados.builder()
                .atleta(atleta)
                .diaPreferidoLongo(DiaSemana.SABADO)
                .build();
        return new Arquetipo("sem-dados", atleta, meta, null,
                contexto(List.of()), INICIO_SEMANA, diasUteis());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Builders base
    // ─────────────────────────────────────────────────────────────────────────

    private static Atleta.AtletaBuilder atletaBase() {
        return Atleta.builder()
                .dataNascimento(LocalDate.of(1990, 5, 20))
                .sexo("M")
                .pesoKg(new BigDecimal("70.0"))
                .alturaCm(new BigDecimal("175.0"))
                .diasDisponiveis(diasUteis())
                .diaPreferidoLongo(DiaSemana.SABADO)
                .temLesao(false);
        // dataUltimoTestePace deixado nulo de propósito: mensagem de pace estável,
        // sem depender de LocalDate.now() no caminho do PaceHistoricoFormatter.
    }

    private static PlanoMetaDados.PlanoMetaDadosBuilder metaBase(Atleta atleta) {
        return PlanoMetaDados.builder()
                .atleta(atleta)
                .diaPreferidoLongo(DiaSemana.SABADO)
                .semanasProgressaoContinua(2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Históricos de treino (datas fixas, anteriores a HOJE)
    // ─────────────────────────────────────────────────────────────────────────

    private static List<TreinoRealizado> historicoLeve() {
        List<TreinoRealizado> t = new ArrayList<>();
        t.add(treino(HOJE.minusDays(2), TipoTreino.CONTINUO, "6.0", 40, 45, 4, 360));
        t.add(treino(HOJE.minusDays(5), TipoTreino.REGENERATIVO, "4.0", 28, 25, 3, 420));
        t.add(treino(HOJE.minusDays(9), TipoTreino.CONTINUO, "7.0", 47, 52, 5, 355));
        return t;
    }

    private static List<TreinoRealizado> historicoModerado() {
        List<TreinoRealizado> t = new ArrayList<>(historicoLeve());
        t.add(treino(HOJE.minusDays(12), TipoTreino.LONGO, "16.0", 95, 120, 6, 350));
        t.add(treino(HOJE.minusDays(16), TipoTreino.INTERVALADO, "10.0", 55, 85, 7, 300));
        return t;
    }

    private static List<TreinoRealizado> historicoPesado() {
        List<TreinoRealizado> t = new ArrayList<>();
        t.add(treino(HOJE.minusDays(1), TipoTreino.INTERVALADO, "12.0", 60, 95, 8, 290));
        t.add(treino(HOJE.minusDays(2), TipoTreino.CONTINUO, "10.0", 55, 70, 6, 330));
        t.add(treino(HOJE.minusDays(3), TipoTreino.LONGO, "24.0", 130, 180, 7, 345));
        t.add(treino(HOJE.minusDays(4), TipoTreino.TIRO, "8.0", 45, 75, 8, 280));
        t.add(treino(HOJE.minusDays(5), TipoTreino.CONTINUO, "12.0", 65, 80, 6, 335));
        return t;
    }

    private static TreinoRealizado treino(LocalDate data, TipoTreino tipo, String km,
                                          long durMin, int tss, int rpe, long paceSegPorKm) {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setDataTreino(data);
        tr.setTipoTreino(tipo);
        tr.setDistanciaKm(new BigDecimal(km));
        tr.setDuracaoMin(Duration.ofMinutes(durMin));
        tr.setTssCalculado(tss);
        tr.setPercepcaoEsforco(rpe);
        tr.setPaceMedia(Duration.ofSeconds(paceSegPorKm));
        return tr;
    }

    private static ContextoTreino contexto(List<TreinoRealizado> treinos) {
        return new ContextoTreino(HOJE, treinos, List.of());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Conjuntos de dias
    // ─────────────────────────────────────────────────────────────────────────

    private static List<DiaSemana> diasUteis() {
        return List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SEXTA, DiaSemana.SABADO);
    }

    private static List<DiaSemana> diasSeisDias() {
        return List.of(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUARTA,
                DiaSemana.QUINTA, DiaSemana.SEXTA, DiaSemana.SABADO);
    }

    private static List<DiaSemana> diasTaper() {
        return List.of(DiaSemana.TERCA, DiaSemana.QUINTA, DiaSemana.SABADO);
    }
}
