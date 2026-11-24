package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.exception.LLMException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * Invariantes "card = editor" e "total = prescrito" sobre a receita completa de
 * {@link NormalizacaoDeTreino#normalizar}, numa grade de cenários dos treinos contínuos
 * (fix-etapas-sintetizadas-encaixe-total).
 *
 * <p><b>Por que uma grade e não casos.</b> O card da revisão mostra o total do treino e o editor soma as
 * etapas; cada divergência relatada até aqui virou um fix pontual, e a seguinte apareceu numa combinação
 * vizinha. A grade varre as combinações que a LLM produz e exige as duas igualdades em todas.</p>
 *
 * <p><b>Tolerância apertada</b> (decisão de 2026-11-24): distância max(2%, 0,05 km), duração 1 min — o
 * que o treinador percebe como "igual". Não são os 10% do contador {@code plano_etapas_total_divergente},
 * que é limiar de telemetria.</p>
 *
 * <p><b>Modelo da entrada.</b> Como a LLM costuma escrever: total concentrado na PRINCIPAL, aquecimento e
 * desaquecimento por tempo, com 0 km. Colaboradores reais, sem mock, com zonas de pace do atleta (como
 * em produção — {@code corrigir-pace-zona} só age com elas).</p>
 *
 * <p><b>Exceções conhecidas</b> são causas catalogadas fora do escopo desta change, cada uma com o
 * follow-up que a resolve. O cenário que cai numa delas é abortado (aparece no relatório com o motivo),
 * não aprovado: a lista é o inventário do que ainda diverge e não cresce sem decisão.</p>
 */
@DisplayName("NormalizacaoDeTreino — invariantes card = editor e total = prescrito (grade)")
class NormalizacaoDeTreinoInvarianteTotalTest {

    private static final UUID ATLETA_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final double TOL_DIST_REL = 0.02;
    private static final double TOL_DIST_ABS_KM = 0.05;
    private static final double TOL_DUR_MIN = 1.0;

    enum Tipo {
        // fator sobre o pace de limiar: ritmo típico da PRINCIPAL de cada tipo
        REGENERATIVO(1.35), CONTINUO(1.20), LONGO(1.15), TEMPO_RUN(1.02);
        final double fator;
        Tipo(double fator) { this.fator = fator; }
    }

    enum Estrutura { COMPLETA, SEM_AQUEC, SEM_DESAQ, SEM_AMBOS }

    enum Ritmo { NA_PRINCIPAL, SO_NO_TREINO, NENHUM }

    enum Cabecalho { IGUAL, DIVERGENTE }

    record Cenario(Tipo tipo, double totalKm, Estrutura estrutura, Ritmo ritmo, double limiar,
                   double fatorDuracao, Cabecalho cabecalho) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%s %.1fkm %s ritmo=%s limiar=%.1f dur×%.1f cab=%s",
                    tipo, totalKm, estrutura, ritmo, limiar, fatorDuracao, cabecalho);
        }
    }

    static Stream<Cenario> grade() {
        List<Cenario> todos = new ArrayList<>();
        for (Tipo tipo : Tipo.values())
            for (double total : new double[]{1.5, 2, 3, 5, 8, 12, 21})
                for (Estrutura estrutura : Estrutura.values())
                    for (Ritmo ritmo : Ritmo.values())
                        for (double limiar : new double[]{4.0, 5.0, 6.0})
                            for (double fatorDuracao : new double[]{1.0, 0.5, 1.5})
                                for (Cabecalho cab : Cabecalho.values()) {
                                    // cabeçalho divergente só existe quando há ritmo no cabeçalho
                                    if (cab == Cabecalho.DIVERGENTE && ritmo == Ritmo.NENHUM) continue;
                                    todos.add(new Cenario(tipo, total, estrutura, ritmo, limiar, fatorDuracao, cab));
                                }
        return todos.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("grade")
    void cardIgualEditorETotalIgualPrescrito(Cenario c) {
        TreinoPlanejadoLlmDto saida;
        try {
            saida = normalizar(c);
        } catch (LLMException e) {
            fail("cenário rejeitado (rejeição nova é falha): " + e.getMessage());
            return;
        }
        List<String> violacoes = violacoes(c, saida);
        if (violacoes.isEmpty()) return;

        Excecao excecao = excecaoDe(c);
        if (excecao != null && toleradas(excecao, violacoes)) {
            abort("exceção conhecida " + excecao.nome() + " — " + excecao.motivo() + " | " + String.join("; ", violacoes));
        }

        assertThat(violacoes).as("invariantes violadas em %s → %s", c, resumo(saida)).isEmpty();
    }

    /**
     * O caso relatado pelo treinador (revisão de plano, 2026-11-24): REGENERATIVO de 2 km / 25 min sem
     * aquecimento virava 5,1 km / 35 min nas etapas. Pela receita completa, com o cabeçalho coerente e
     * com o ritmo do cabeçalho divergente do da PRINCIPAL (CA2).
     */
    @ParameterizedTest(name = "ritmo do cabeçalho {0}")
    @CsvSource({"6:30-7:00/km,25:00", "8:00-8:00/km,16:00"})
    void casoRelatadoViraBlocoUnicoComDuracaoAlinhada(String ritmoCabecalho, String duracaoLlm) {
        var meter = new SimpleMeterRegistry();
        Atleta atleta = Atleta.builder().id(ATLETA_ID).nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .paceLimiar(BigDecimal.valueOf(5.0)).build();
        var normalizacao = new NormalizacaoDeTreino(new TreinoNormalizador(new PaceValidator()), new EtapaFcValidator(),
                new PlanoEstruturaReparador(new SimpleMeterRegistry()), new PaceValidator(), meter);
        var entrada = new TreinoPlanejadoLlmDto("QUARTA", "REGENERATIVO", null, 15, 0.5, 2, "Retorno com cautela",
                duracaoLlm, 2.0, ritmoCabecalho, List.of(
                new EtapaTreinoLlmDto(1, "PRINCIPAL", "Caminhe e trote muito leve", 20, 2.0, null, 1, "6:30-7:00/km"),
                new EtapaTreinoLlmDto(2, "DESAQUECIMENTO", "Mobilidade suave", 5, 0.0, null, 1, null)));

        var saida = normalizacao.normalizar(entrada, new ContextoNormalizacao(atleta, ATLETA_ID, null, Map.of(), Map.of(),
                ContextoNormalizacao.zonasPaceComDado(atleta, new ZonaTreinoService())));

        assertThat(saida.etapas()).singleElement().satisfies(e -> {
            assertThat(e.tipoEtapa()).isEqualTo("PRINCIPAL");
            assertThat(e.distanciaKm()).isEqualTo(2.0);
        });
        assertThat(saida.distanciaKm()).isEqualTo(2.0);
        assertThat(minutos(saida.duracaoMin())).isEqualTo((double) saida.etapas().get(0).duracaoMin());
        assertThat(meter.find("plano_etapas_total_divergente").counter()).isNull();
    }

    // ======================================================================================
    // Invariantes
    // ======================================================================================

    static List<String> violacoes(Cenario c, TreinoPlanejadoLlmDto t) {
        List<String> v = new ArrayList<>();
        double somaKm = somaKm(t);
        if (t.distanciaKm() == null) {
            v.add("total sem distância");
        } else {
            if (!distIgual(somaKm, t.distanciaKm())) {
                v.add(String.format(Locale.ROOT, "card≠editor distância: total %.2f, etapas %.2f", t.distanciaKm(), somaKm));
            }
            if (!distIgual(t.distanciaKm(), c.totalKm())) {
                v.add(String.format(Locale.ROOT, "total≠prescrito: prescrito %.2f, final %.2f", c.totalKm(), t.distanciaKm()));
            }
        }
        Double durTotal = minutos(t.duracaoMin());
        double somaMin = somaMin(t);
        if (durTotal == null) {
            v.add("total sem duração");
        } else if (Math.abs(durTotal - somaMin) > TOL_DUR_MIN) {
            v.add(String.format(Locale.ROOT, "card≠editor duração: total %.0f, etapas %.0f", durTotal, somaMin));
        }
        return v;
    }

    private static boolean distIgual(double a, double b) {
        return Math.abs(a - b) <= Math.max(TOL_DIST_REL * Math.max(a, b), TOL_DIST_ABS_KM);
    }

    static double somaKm(TreinoPlanejadoLlmDto t) {
        return t.etapas() == null ? 0 : t.etapas().stream()
                .mapToDouble(e -> e.distanciaKm() == null ? 0 : e.distanciaKm() * rep(e)).sum();
    }

    static double somaMin(TreinoPlanejadoLlmDto t) {
        return t.etapas() == null ? 0 : t.etapas().stream()
                .mapToDouble(e -> e.duracaoMin() == null ? 0 : e.duracaoMin() * rep(e)).sum();
    }

    private static int rep(EtapaTreinoLlmDto e) {
        return e.repeticoes() == null || e.repeticoes() < 1 ? 1 : e.repeticoes();
    }

    /** "MM:SS" ou "HH:MM:SS" → minutos. */
    static Double minutos(String duracao) {
        if (duracao == null || duracao.isBlank()) return null;
        String[] p = duracao.trim().split(":");
        try {
            if (p.length == 2) return Integer.parseInt(p[0]) + Integer.parseInt(p[1]) / 60.0;
            if (p.length == 3) return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]) + Integer.parseInt(p[2]) / 60.0;
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    // ======================================================================================
    // Exceções conhecidas — causas fora do escopo desta change (ver tasks.md, "Achados da matriz")
    // ======================================================================================

    static final String CARD_DIST = "card≠editor distância";
    static final String CARD_DUR = "card≠editor duração";
    static final String TOTAL = "total≠prescrito";

    /**
     * Causa catalogada: quais cenários ela cobre e quais violações tolera neles. Uma violação de outro tipo
     * num cenário coberto continua sendo falha — a exceção não vira passe livre para regressão.
     */
    record Excecao(String nome, String motivo, Predicate<Cenario> cobre, Set<String> tolera) {}

    static final List<Excecao> EXCECOES = List.of(
            new Excecao("sem-pace", "PRINCIPAL sem pace derivável: o total da LLM fica e as etapas não encaixam "
                    + "(follow-up: derivar o pace da zona do atleta, A9)",
                    c -> c.ritmo() == Ritmo.NENHUM, Set.of(CARD_DIST)),
            new Excecao("tempo-run-sintetizado", "TEMPO_RUN com etapa sintetizada: bloco único seria limiar sem "
                    + "aquecimento (follow-up: rejeitar e pedir a estrutura no turno de reparo, A6)",
                    c -> c.tipo() == Tipo.TEMPO_RUN && c.estrutura() != Estrutura.COMPLETA, Set.of(CARD_DIST, CARD_DUR)),
            new Excecao("estrutura-completa", "estrutura completa da LLM: adotarSomaDasEtapas troca o total prescrito "
                    + "pela soma e a duração fica a da LLM quando o ritmo do cabeçalho diverge (follow-up: total = prescrito)",
                    c -> c.estrutura() == Estrutura.COMPLETA, Set.of(TOTAL, CARD_DUR)));

    /**
     * Inventário congelado: quantos cenários cada causa cobre com violação hoje. Mudar um número é decisão —
     * cair é bom (atualize o número), subir é regressão ou causa nova a catalogar.
     */
    static final Map<String, Integer> INVENTARIO = Map.of(
            "sem-pace", 504,
            "tempo-run-sintetizado", 252,
            "estrutura-completa", 1004);

    static Excecao excecaoDe(Cenario c) {
        return EXCECOES.stream().filter(e -> e.cobre().test(c)).findFirst().orElse(null);
    }

    static boolean toleradas(Excecao e, List<String> violacoes) {
        return violacoes.stream().allMatch(v -> e.tolera().stream().anyMatch(v::startsWith));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("inventário das exceções conhecidas não muda sem decisão")
    void inventarioDasExcecoes() {
        Map<String, Integer> contagem = new java.util.TreeMap<>();
        grade().forEach(c -> {
            Excecao e = excecaoDe(c);
            if (e != null && !violacoes(c, normalizar(c)).isEmpty()) contagem.merge(e.nome(), 1, Integer::sum);
        });
        assertThat(contagem).isEqualTo(new java.util.TreeMap<>(INVENTARIO));
    }

    // ======================================================================================
    // Entrada (o que a LLM "gerou") e receita
    // ======================================================================================

    static TreinoPlanejadoLlmDto normalizar(Cenario c) {
        Atleta atleta = Atleta.builder()
                .id(ATLETA_ID)
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .paceLimiar(BigDecimal.valueOf(c.limiar()))
                .build();
        var contexto = new ContextoNormalizacao(atleta, ATLETA_ID, null, Map.of(), Map.of(),
                ContextoNormalizacao.zonasPaceComDado(atleta, new ZonaTreinoService()));
        var normalizacao = new NormalizacaoDeTreino(
                new TreinoNormalizador(new PaceValidator()),
                new EtapaFcValidator(),
                new PlanoEstruturaReparador(new SimpleMeterRegistry()),
                new PaceValidator(),
                new SimpleMeterRegistry());
        return normalizacao.normalizar(entrada(c), contexto);
    }

    static TreinoPlanejadoLlmDto entrada(Cenario c) {
        double pace = c.limiar() * c.tipo().fator;
        String ritmoEtapa = faixa(pace);
        int principalMin = Math.max(1, (int) Math.round(c.totalKm() * pace));

        List<EtapaTreinoLlmDto> etapas = new ArrayList<>();
        boolean aquec = c.estrutura() == Estrutura.COMPLETA || c.estrutura() == Estrutura.SEM_DESAQ;
        boolean desaq = c.estrutura() == Estrutura.COMPLETA || c.estrutura() == Estrutura.SEM_AQUEC;
        int ordem = 1;
        if (aquec) etapas.add(new EtapaTreinoLlmDto(ordem++, "AQUECIMENTO", "Aquecimento leve", 10, 0.0, null, 1, null));
        etapas.add(new EtapaTreinoLlmDto(ordem++, "PRINCIPAL", "Principal", principalMin, c.totalKm(), null, 1,
                c.ritmo() == Ritmo.NA_PRINCIPAL ? ritmoEtapa : null));
        if (desaq) etapas.add(new EtapaTreinoLlmDto(ordem, "DESAQUECIMENTO", "Desaquecimento leve", 5, 0.0, null, 1, null));

        String ritmoCabecalho = c.ritmo() == Ritmo.NENHUM ? null
                : c.cabecalho() == Cabecalho.DIVERGENTE ? "8:00-8:00/km" : ritmoEtapa;
        double paceCabecalho = c.cabecalho() == Cabecalho.DIVERGENTE ? 8.0 : pace;
        int duracaoLlm = Math.max(1, (int) Math.round(c.totalKm() * paceCabecalho * c.fatorDuracao()));

        return new TreinoPlanejadoLlmDto("QUARTA", c.tipo().name(), null, 30, 0.6, 3,
                "Cenário da grade", String.format(Locale.ROOT, "%02d:00", duracaoLlm), c.totalKm(),
                ritmoCabecalho, etapas);
    }

    /** Faixa de ±0,15 min/km em torno do pace, no formato que a LLM escreve. */
    private static String faixa(double pace) {
        return mmss(pace - 0.15) + "-" + mmss(pace + 0.15) + "/km";
    }

    private static String mmss(double minPorKm) {
        int total = (int) Math.round(minPorKm * 60);
        return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60);
    }

    static String resumo(TreinoPlanejadoLlmDto t) {
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "total %skm/%s ritmo=%s [",
                t.distanciaKm(), t.duracaoMin(), t.ritmoAlvo()));
        if (t.etapas() != null) {
            for (EtapaTreinoLlmDto e : t.etapas()) {
                sb.append(String.format(Locale.ROOT, "%s %skm/%smin%s; ", e.tipoEtapa(), e.distanciaKm(), e.duracaoMin(),
                        Objects.toString(e.ritmoAlvo(), "")));
            }
        }
        return sb.append(']').toString();
    }
}
