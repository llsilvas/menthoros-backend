package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.exception.LLMException;
import br.com.menthoros.backend.services.prompt.PaceHistoricoFormatter;
import br.com.menthoros.backend.services.prompt.constraint.Constraint;
import br.com.menthoros.backend.services.quality.PlanQualityChecker;
import br.com.menthoros.backend.services.quality.ViolacaoQualidade;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Invariante "normalizado não viola PACE_TETO" (fix-pace-teto-invariante): para todo treino que
 * {@link NormalizacaoDeTreino#normalizar} aceita, {@link PlanQualityChecker} com a regra PACE_TETO do mesmo
 * teto não acusa nada. Corretor e verificador vivem em classes diferentes, com parsers diferentes; a grade
 * é a rede que pega a divergência.
 *
 * <p>Teto e piso saem de {@link PaceHistoricoFormatter} sobre histórico sintético, como em produção.
 * PROVA fica fora: a prova real é inserida depois da normalização e tem teste próprio no checker. Texto livre
 * com m:ss também: não há como reescrevê-lo, e o checker continua acusando (CA8).</p>
 *
 * <p>Em develop (9ba2f61) a grade inteira era aceita e 578 cenários violavam, em quatro causas: etapa sem
 * zonas de pace, tipo fora da caixa canônica, piso desfazendo o teto e formato não canônico. Sem exceções
 * hoje; rejeição nova é falha, porque faria o invariante passar vazio.</p>
 */
@DisplayName("NormalizacaoDeTreino — invariante normalizado não viola PACE_TETO (grade)")
class NormalizacaoDeTreinoInvarianteTetoTest {

    private static final UUID ATLETA_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final PaceHistoricoFormatter HISTORICO = new PaceHistoricoFormatter();

    /** Tipo como a LLM escreve; o último é a variante de caixa e espaço (D6). */
    enum Tipo {
        REGENERATIVO("REGENERATIVO"), FACIL("FACIL"), CONTINUO("CONTINUO"), LONGO("LONGO"),
        TEMPO_RUN("TEMPO_RUN"), FARTLEK("FARTLEK"), INTERVALADO("INTERVALADO"), TIRO("TIRO"),
        SUBIDA("SUBIDA"), FACIL_MINUSCULO(" facil ");
        final String texto;
        Tipo(String texto) { this.texto = texto; }
        TipoTreino enumDoTeto() { return TipoTreino.valueOf(texto.trim().toUpperCase()); }
        boolean intensivo() { return this == FARTLEK || this == INTERVALADO || this == TIRO || this == SUBIDA; }
    }

    /** Histórico do tipo: melhor pace (base) e dispersão, que definem teto (base × 0,98) e piso (média × 1,25). */
    enum Historico {
        RAPIDO_ESTREITO(300, 10), RAPIDO_LARGO(300, 120), LENTO_ESTREITO(450, 10), LENTO_LARGO(450, 120);
        final int baseSeg;
        final int dispersaoSeg;
        Historico(int baseSeg, int dispersaoSeg) { this.baseSeg = baseSeg; this.dispersaoSeg = dispersaoSeg; }
    }

    /** Borda rápida do ritmo prescrito em relação ao teto. */
    enum Posicao {
        MAIS_RAPIDO(-30), IGUAL(0), MAIS_LENTO(30);
        final int deltaSeg;
        Posicao(int deltaSeg) { this.deltaSeg = deltaSeg; }
    }

    enum Formato { CANONICO, LENIENTE_ESPACO, SEM_KM, UNICO }

    enum Amplitude {
        ESTREITA(15), LARGA(150);
        final int seg;
        Amplitude(int seg) { this.seg = seg; }
    }

    record Cenario(Tipo tipo, boolean comZonas, Historico historico, Posicao posicao, Formato formato,
                   Amplitude amplitude) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%s %s %s %s %s %s", tipo, comZonas ? "zonas" : "sem-zonas",
                    historico, posicao, formato, amplitude);
        }
    }

    static Stream<Cenario> grade() {
        List<Cenario> todos = new ArrayList<>();
        for (Tipo tipo : Tipo.values())
            for (boolean comZonas : new boolean[]{true, false})
                for (Historico h : Historico.values())
                    for (Posicao p : Posicao.values())
                        for (Formato f : Formato.values())
                            for (Amplitude a : Amplitude.values()) {
                                // ritmo único não tem amplitude
                                if (f == Formato.UNICO && a == Amplitude.LARGA) continue;
                                todos.add(new Cenario(tipo, comZonas, h, p, f, a));
                            }
        return todos.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("grade")
    void normalizadoNaoViolaPaceTeto(Cenario c) {
        List<TreinoRealizado> historico = historico(c);
        Map<TipoTreino, BigDecimal> teto = HISTORICO.calcularTetoPorTipo(historico);
        Map<TipoTreino, BigDecimal> piso = HISTORICO.calcularPisoPorTipo(historico);

        TreinoPlanejadoLlmDto saida;
        try {
            saida = normalizar(c, teto, piso);
        } catch (LLMException e) {
            // develop aceita a grade inteira: rejeição nova faria o invariante passar vazio
            fail("cenário rejeitado (rejeição nova é falha): " + e.getMessage());
            return;
        }

        List<String> violacoes = new PlanQualityChecker(new SimpleMeterRegistry())
                .check(plano(saida), List.of(Constraint.paceTeto("Teto.", teto))).stream()
                .map(ViolacaoQualidade::mensagem).toList();

        assertThat(violacoes).as("PACE_TETO depois de normalizar em %s → %s", c, resumo(saida)).isEmpty();
    }

    private static TreinoPlanejadoLlmDto normalizar(Cenario c, Map<TipoTreino, BigDecimal> teto,
                                                    Map<TipoTreino, BigDecimal> piso) {
        Atleta atleta = Atleta.builder().id(ATLETA_ID).nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .paceLimiar(c.comZonas() ? BigDecimal.valueOf(5.0) : null).build();
        var normalizacao = new NormalizacaoDeTreino(new TreinoNormalizador(new PaceValidator()), new EtapaFcValidator(),
                new PlanoEstruturaReparador(new SimpleMeterRegistry()), new PaceValidator(), new SimpleMeterRegistry());
        return normalizacao.normalizar(entrada(c), new ContextoNormalizacao(atleta, ATLETA_ID, null, teto, piso,
                ContextoNormalizacao.zonasPaceComDado(atleta, new ZonaTreinoService())));
    }

    /** Como a LLM costuma escrever: ritmo no cabeçalho e na PRINCIPAL, aquecimento e desaquecimento por tempo. */
    private static TreinoPlanejadoLlmDto entrada(Cenario c) {
        String ritmo = ritmo(c);
        List<EtapaTreinoLlmDto> etapas = c.tipo().intensivo()
                ? intensivas(ritmo)
                : List.of(
                        new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento", 10, 0.0, null, 1, null),
                        new EtapaTreinoLlmDto(2, "PRINCIPAL", "Bloco principal", 40, 8.0, null, 1, ritmo),
                        new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Desaquecimento", 5, 0.0, null, 1, null));
        double km = c.tipo().intensivo() ? 4.4 : 8.0;
        return new TreinoPlanejadoLlmDto("QUARTA", c.tipo().texto, null, 50, 0.7, 5, "Treino da grade",
                "55:00", km, ritmo, etapas);
    }

    /** Intensivos com as repetições expandidas, como a receita exige (repeticoes = 1, uma etapa por tiro). */
    private static List<EtapaTreinoLlmDto> intensivas(String ritmo) {
        List<EtapaTreinoLlmDto> etapas = new ArrayList<>();
        etapas.add(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento", 15, 0.0, null, 1, null));
        for (int i = 0; i < 4; i++) {
            etapas.add(new EtapaTreinoLlmDto(etapas.size() + 1, "INTERVALADO", "Tiro", 3, 0.8, null, 1, ritmo));
            etapas.add(new EtapaTreinoLlmDto(etapas.size() + 1, "RECUPERACAO", "Trote", 2, 0.0, null, 1, null));
        }
        etapas.add(new EtapaTreinoLlmDto(etapas.size() + 1, "DESAQUECIMENTO", "Desaquecimento", 10, 0.0, null, 1, null));
        return etapas;
    }

    /** Borda rápida = teto + posição; borda lenta = borda rápida + amplitude. */
    private static String ritmo(Cenario c) {
        int tetoSeg = (int) Math.ceil(Math.round(c.historico().baseSeg * 0.98 * 10) / 10.0);
        int rapido = tetoSeg + c.posicao().deltaSeg;
        int lento = rapido + c.amplitude().seg;
        return switch (c.formato()) {
            case CANONICO -> mmss(rapido) + "-" + mmss(lento) + "/km";
            case LENIENTE_ESPACO -> mmss(rapido) + " - " + mmss(lento) + "/km";
            case SEM_KM -> mmss(rapido) + "-" + mmss(lento);
            case UNICO -> mmss(rapido) + "/km";
        };
    }

    /** Dois treinos realizados do tipo: o melhor (base) e um mais lento (base + dispersão). */
    private static List<TreinoRealizado> historico(Cenario c) {
        TipoTreino tipo = c.tipo().enumDoTeto();
        return List.of(realizado(tipo, c.historico().baseSeg),
                realizado(tipo, c.historico().baseSeg + c.historico().dispersaoSeg));
    }

    private static TreinoRealizado realizado(TipoTreino tipo, int paceSeg) {
        TreinoRealizado t = new TreinoRealizado();
        t.setTipoTreino(tipo);
        t.setPaceMedia(Duration.ofSeconds(paceSeg));
        return t;
    }

    private static PlanoSemanalLlmDto plano(TreinoPlanejadoLlmDto treino) {
        return new PlanoSemanalLlmDto(0.0, 0.0, null, null, null, null, List.of(treino), List.of());
    }

    private static String mmss(int seg) {
        return String.format(Locale.ROOT, "%d:%02d", seg / 60, seg % 60);
    }

    private static String resumo(TreinoPlanejadoLlmDto t) {
        StringBuilder sb = new StringBuilder("cab=").append(t.ritmoAlvo());
        if (t.etapas() != null) {
            for (EtapaTreinoLlmDto e : t.etapas()) sb.append(' ').append(e.tipoEtapa()).append('=').append(e.ritmoAlvo());
        }
        return sb.toString();
    }
}
