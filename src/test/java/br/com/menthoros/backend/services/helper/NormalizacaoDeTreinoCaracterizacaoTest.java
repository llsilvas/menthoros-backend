package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.exception.LLMException;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaFC;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rede de caracterização de {@link NormalizacaoDeTreino#normalizar} — a receita completa que a
 * geração aplica a cada treino da resposta da LLM (correção temporal → expansão → gates
 * estruturais → normalização → reparo → validação por tipo → FC → pace → duração → distância →
 * triângulo). Testar a composição pela interface do module, não os passos isolados, é a rede de
 * segurança da ordem: os 2 bugs de ordem achados no `/qa` de refactor-iaservice-decomposition eram
 * invisíveis aos testes das peças.
 *
 * <p><b>Baseline capturado em {@code develop} 72304b9 ANTES de qualquer refactor</b>
 * (pipeline-normalizacao-treino, task 0.4, então via {@code PlanoLlmValidator}; a task 3.1 trocou só
 * o arranjo — os records esperados são byte a byte os mesmos). Cada record esperado foi obtido
 * rodando uma vez e copiando a saída — é caracterização, não especificação: congela o que o sistema
 * faz hoje, inclusive o que parece estranho (ex.: aquec/desaq sintetizados pelo reparo saem com
 * {@code distanciaKm = null}). Mudar um esperado aqui é mudar comportamento, e exige decisão.</p>
 *
 * <p>Colaboradores reais em tudo ({@code TreinoNormalizador}, {@code EtapaFcValidator},
 * {@code PaceValidator}, {@code PlanoEstruturaReparador}); sem mock nenhum — o
 * {@link ContextoNormalizacao} é montado à mão (zonas de FC só no atleta com FC, teto/piso de pace
 * vazios). paceLimiar = 5.0 min/km em todo cenário (paceZ2 = 6.0, paceZ1 = 6.75).</p>
 */
@DisplayName("NormalizacaoDeTreino — caracterização da composição completa (baseline 72304b9)")
class NormalizacaoDeTreinoCaracterizacaoTest {

    private static final UUID ATLETA_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final List<ZonaFC> ZONAS_FC_160 = List.of(
            new ZonaFC(1, "Recuperação", 120, 136),
            new ZonaFC(2, "Aeróbico",    136, 142),
            new ZonaFC(3, "Tempo",       142, 150),
            new ZonaFC(4, "Limiar",      150, 160),
            new ZonaFC(5, "VO2max",      160, 170));

    private Atleta atletaSemFc;
    private Atleta atletaComFc;

    @BeforeEach
    void setUp() {
        atletaSemFc = atleta(false);
        atletaComFc = atleta(true);
    }

    // ======================================================================================
    // Aceitação — record completo
    // ======================================================================================

    @Nested
    @DisplayName("aceitação: record completo idêntico ao baseline")
    class Aceitacao {

        @Test
        @DisplayName("INTERVALADO já expandido: gap positivo cresce tiros (1.0→1.1) e IA-05 recalcula duração (4→5)")
        void intervalado() {
            var esperado = new TreinoPlanejadoLlmDto("SEGUNDA", "INTERVALADO", "150-160 bpm", 60, 1.1, 8,
                    "Desenvolver VO2max", "29:00", 5.3, "4:30-5:00/km",
                    List.of(
                            new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento leve Z1-Z2", 10, 1.67, "120-136 bpm", 1, null),
                            new EtapaTreinoLlmDto(2, "INTERVALADO", "Tiro 1 - Z5", 5, 1.1, "160-170 bpm", 1, "4:00-4:15/km"),
                            new EtapaTreinoLlmDto(3, "RECUPERACAO", "Recuperação 1 - trote Z2", 2, 0.3, "120-136 bpm", 1, null),
                            new EtapaTreinoLlmDto(4, "INTERVALADO", "Tiro 2 - Z5", 5, 1.1, "160-170 bpm", 1, "4:00-4:15/km"),
                            new EtapaTreinoLlmDto(5, "RECUPERACAO", "Recuperação 2 - trote Z2", 2, 0.3, "120-136 bpm", 1, null),
                            new EtapaTreinoLlmDto(6, "DESAQUECIMENTO", "Desaquecimento leve Z1", 5, 0.83, "120-136 bpm", 1, null)),
                    null, null, null);

            assertThat(normalizar(atletaSemFc, entradaIntervalado())).isEqualTo(esperado);
        }

        @Test
        @DisplayName("LONGO: etapas pelo pace (fix-etapas-continuos-pace) e total reconciliado com a soma")
        void longo() {
            // fix-etapas-continuos-pace: PRINCIPAL pelo pace (60 ÷ 5,75 = 10,43), aquec/desaq a Z2 (6,0);
            // a soma 12,93 desvia 14% dos 15 km da LLM (que a 5:30-6:00 esperaria 86min) → total = soma
            var esperado = new TreinoPlanejadoLlmDto("SABADO", "LONGO", "136-150 bpm", 90, 0.85, 6,
                    "Construir base aeróbica", "75:00", 12.93, "5:30-6:00/km",
                    List.of(
                            new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento leve Z1-Z2", 10, 1.67, "120-136 bpm", 1, null),
                            new EtapaTreinoLlmDto(2, "PRINCIPAL", "Longo contínuo Z2-Z3", 60, 10.43, "136-150 bpm", 1, "5:30-6:00/km"),
                            new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Desaquecimento leve Z1", 5, 0.83, "120-136 bpm", 1, null)),
                    null, null, null);

            assertThat(normalizar(atletaSemFc, entradaLongo())).isEqualTo(esperado);
        }

        @Test
        @DisplayName("REGENERATIVO: etapas pelo pace e total = soma das etapas (tolerância zero)")
        void regenerativo() {
            // fix-etapas-continuos-pace: PRINCIPAL 25 ÷ 6,75 = 3,70; aquec/desaq 5 ÷ 6,0 = 0,83; com toda
            // etapa pelo pace o total é a soma (tolerância zero): 5,0 → 5,36
            var esperado = new TreinoPlanejadoLlmDto("QUARTA", "REGENERATIVO", "115-130 bpm", 25, 0.6, 3,
                    "Recuperação ativa", "35:00", 5.36, "6:30-7:00/km",
                    List.of(
                            new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento muito leve", 5, 0.83, "115-130 bpm", 1, null),
                            new EtapaTreinoLlmDto(2, "PRINCIPAL", "Trote regenerativo Z1", 25, 3.7, "115-130 bpm", 1, "6:30-7:00/km"),
                            new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Desaquecimento leve", 5, 0.83, "115-130 bpm", 1, null)),
                    null, null, null);

            assertThat(normalizar(atletaSemFc, entradaRegenerativo())).isEqualTo(esperado);
        }

        @Test
        @DisplayName("FARTLEK com zonas FC: '4x (1min Z2 + 2min Z1)' expande em 4 acelerações Z2 + 4 recuperações Z1 (IA-02)")
        void fartlekComZonas() {
            // fix-fartlek-etapas-estruturadas: sem ritmoAlvo na série a distância das acelerações é
            // desconhecida (0.0) — a reconciliação mantém os 7 km e o triângulo os 40:00 da LLM. A
            // baseline anterior (5,34 km / 32:00) vinha de repartir os 2 km da PRINCIPAL pela série
            var esperado = new TreinoPlanejadoLlmDto("QUINTA", "FARTLEK", "136-160 bpm", 55, 1.0, 7,
                    "Variação de ritmo", "40:00", 7.0, "5:00-5:45/km",
                    List.of(
                            new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 10, 1.67, "120-136 bpm", 1, null),
                            new EtapaTreinoLlmDto(2, "INTERVALADO", "Aceleração 1/4 - 1min", 1, 0.0, "136-142 bpm", 1, null),
                            new EtapaTreinoLlmDto(3, "RECUPERACAO", "Recuperação 1 - 2min trote", 2, 0.3, "120-136 bpm", 1, null),
                            new EtapaTreinoLlmDto(4, "INTERVALADO", "Aceleração 2/4 - 1min", 1, 0.0, "136-142 bpm", 1, null),
                            new EtapaTreinoLlmDto(5, "RECUPERACAO", "Recuperação 2 - 2min trote", 2, 0.3, "120-136 bpm", 1, null),
                            new EtapaTreinoLlmDto(6, "INTERVALADO", "Aceleração 3/4 - 1min", 1, 0.0, "136-142 bpm", 1, null),
                            new EtapaTreinoLlmDto(7, "RECUPERACAO", "Recuperação 3 - 2min trote", 2, 0.3, "120-136 bpm", 1, null),
                            new EtapaTreinoLlmDto(8, "INTERVALADO", "Aceleração 4/4 - 1min", 1, 0.0, "136-142 bpm", 1, null),
                            new EtapaTreinoLlmDto(9, "RECUPERACAO", "Recuperação 4 - 2min trote", 2, 0.3, "120-136 bpm", 1, null),
                            new EtapaTreinoLlmDto(10, "DESAQUECIMENTO", "Caminhada", 10, 1.67, "120-136 bpm", 1, null)),
                    null, null, null);

            assertThat(normalizar(atletaComFc, entradaFartlek())).isEqualTo(esperado);
        }

        @Test
        @DisplayName("FACIL (família PADRAO): só a cauda comum, atravessa intacto")
        void facil() {
            var esperado = new TreinoPlanejadoLlmDto("TERCA", "FACIL", "130-145 bpm", 40, 0.7, 4,
                    "Rodagem fácil", "40:00", 6.5, "6:00-6:30/km",
                    List.of(
                            new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 5, 0.8, "120-136 bpm", 1, null),
                            new EtapaTreinoLlmDto(2, "PRINCIPAL", "Rodagem confortável", 30, 5.0, "130-145 bpm", 1, "6:00-6:30/km"),
                            new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Caminhada", 5, 0.7, "120-136 bpm", 1, null)),
                    null, null, null);

            assertThat(normalizar(atletaSemFc, entradaFacil())).isEqualTo(esperado);
        }

        @Test
        @DisplayName("INTERVALADO comprimido '5x800m' com 3 etapas: só passa em gate-contagem porque expandir roda antes")
        void intervaladoComprimido() {
            var esperado = new TreinoPlanejadoLlmDto("SEGUNDA", "INTERVALADO", "150-160 bpm", 60, 1.1, 8,
                    "Desenvolver VO2max", "50:00", 8.17, "4:30-5:00/km",
                    List.of(
                            new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento leve", 10, 1.67, "120-136 bpm", 1, null),
                            new EtapaTreinoLlmDto(2, "INTERVALADO", "Intervalo 1/5 - Z5", 4, 0.8, "150-160 bpm", 1, "4:00-4:15/km"),
                            new EtapaTreinoLlmDto(3, "RECUPERACAO", "Recuperação 1 - trote Z2", 2, 0.2, "60-70% FCmax", 1, null),
                            new EtapaTreinoLlmDto(4, "INTERVALADO", "Intervalo 2/5 - Z5", 4, 0.8, "150-160 bpm", 1, "4:00-4:15/km"),
                            new EtapaTreinoLlmDto(5, "RECUPERACAO", "Recuperação 2 - trote Z2", 2, 0.2, "60-70% FCmax", 1, null),
                            new EtapaTreinoLlmDto(6, "INTERVALADO", "Intervalo 3/5 - Z5", 4, 0.8, "150-160 bpm", 1, "4:00-4:15/km"),
                            new EtapaTreinoLlmDto(7, "RECUPERACAO", "Recuperação 3 - trote Z2", 2, 0.2, "60-70% FCmax", 1, null),
                            new EtapaTreinoLlmDto(8, "INTERVALADO", "Intervalo 4/5 - Z5", 4, 0.8, "150-160 bpm", 1, "4:00-4:15/km"),
                            new EtapaTreinoLlmDto(9, "RECUPERACAO", "Recuperação 4 - trote Z2", 2, 0.2, "60-70% FCmax", 1, null),
                            new EtapaTreinoLlmDto(10, "INTERVALADO", "Intervalo 5/5 - Z5", 4, 0.8, "150-160 bpm", 1, "4:00-4:15/km"),
                            new EtapaTreinoLlmDto(11, "RECUPERACAO", "Recuperação 5 - trote Z2", 2, 0.2, "60-70% FCmax", 1, null),
                            new EtapaTreinoLlmDto(12, "DESAQUECIMENTO", "Desaquecimento leve", 10, 1.5, "120-136 bpm", 1, null)),
                    null, null, null);

            assertThat(normalizar(atletaSemFc, entradaIntervaladoComprimido())).isEqualTo(esperado);
        }

        @Test
        @DisplayName("REGENERATIVO só com PRINCIPAL: só passa em validar-por-tipo porque reparar-3-etapas sintetiza aquec/desaq antes; etapas pelo pace, total e duração da LLM mantidos")
        void regenerativoSoPrincipal() {
            // Decisão (fix-normalizador-etapas-incompletas): o baseline original era "45:00" — a soma das
            // etapas (10 sintetizado + 30 + 5 sintetizado) sobrescrevia o "30:00" da LLM e produzia 4 km
            // em 45 min a 6:30-7:00/km. Agora recalcular-duracao vê que 30:00 fecha com pace × distância
            // (27 min) e a soma não (67% de desvio), e mantém a duração da LLM.
            // fix-etapas-continuos-pace: as etapas ganham distância pelo pace (PRINCIPAL 30 ÷ 6,75 = 4,44,
            // aquec/desaq a Z2), mas o total NÃO é reconciliado — aquec/desaq foram sintetizados pelo
            // reparo e se somam à prescrição; reconciliar levaria o regenerativo a 45min/6,94km.
            var esperado = new TreinoPlanejadoLlmDto("QUARTA", "REGENERATIVO", "115-130 bpm", 25, 0.6, 3,
                    "Recuperação ativa", "30:00", 4.0, "6:30-7:00/km",
                    List.of(
                            new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento leve em Z1-Z2 (gerado pelo sistema)", 10, 1.67, null, 1, null),
                            new EtapaTreinoLlmDto(2, "PRINCIPAL", "Trote regenerativo Z1", 30, 4.44, "115-130 bpm", 1, "6:30-7:00/km"),
                            new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Desaquecimento leve em Z1 (gerado pelo sistema)", 5, 0.83, null, 1, null)),
                    null, null, null);

            assertThat(normalizar(atletaSemFc, entradaRegenerativoSoPrincipal())).isEqualTo(esperado);
        }

        @Test
        @DisplayName("repeticoes == null é aceito por validar-repeticoes (semântica de null preservada)")
        void repeticoesNull() {
            // fix-etapas-continuos-pace: mesmas distâncias do caso longo — repeticoes null não interfere
            var esperado = new TreinoPlanejadoLlmDto("SABADO", "LONGO", "136-150 bpm", 90, 0.85, 6,
                    "Base", "75:00", 12.93, "5:30-6:00/km",
                    List.of(
                            new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento leve", 10, 1.67, "120-136 bpm", null, null),
                            new EtapaTreinoLlmDto(2, "PRINCIPAL", "Longo contínuo", 60, 10.43, "136-150 bpm", null, "5:30-6:00/km"),
                            new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Desaquecimento leve", 5, 0.83, "120-136 bpm", null, null)),
                    null, null, null);

            assertThat(normalizar(atletaSemFc, entradaRepeticoesNull())).isEqualTo(esperado);
        }
    }

    // ======================================================================================
    // Rejeição — LLMException com a mensagem do gate
    // ======================================================================================

    @Nested
    @DisplayName("rejeição: os gates estruturais continuam derrubando o treino")
    class Rejeicao {

        @Test
        @DisplayName("4 etapas não é mascarado pelo padding: gate-contagem roda ANTES de normalizar-intervalado")
        void padding4Etapas() {
            assertThatThrownBy(() -> normalizar(atletaSemFc, entradaPadding4Etapas()))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("mínimo 6");
        }

        @Test
        @DisplayName("tiro que passa de 10min após o crescimento de distância (IA-05): gate-duracao-tiros roda DEPOIS de normalizar")
        void tiroPassaDe10min() {
            assertThatThrownBy(() -> normalizar(atletaSemFc, entradaTiroPassaDe10min()))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("duração incoerente");
        }

        @Test
        @DisplayName("tiro com duracaoMin == null é rejeitado antes de qualquer normalização (null conta como inválido)")
        void tiroDuracaoNull() {
            assertThatThrownBy(() -> normalizar(atletaSemFc, entradaTiroDuracaoNull()))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("duração incoerente");
        }

        @Test
        @DisplayName("RECUPERACAO antes do 1º tiro: gate-sequencia rejeita mesmo com contagem, extremos e balanceamento ok")
        void recuperacaoSemTiro() {
            assertThatThrownBy(() -> normalizar(atletaSemFc, entradaRecuperacaoSemTiro()))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("recuperações sem tiro");
        }
    }

    // ======================================================================================
    // Arranjo
    // ======================================================================================

    private TreinoPlanejadoLlmDto normalizar(Atleta atleta, TreinoPlanejadoLlmDto treino) {
        return normalizacao().normalizar(treino, contexto(atleta));
    }

    private static NormalizacaoDeTreino normalizacao() {
        return new NormalizacaoDeTreino(
                new TreinoNormalizador(new PaceValidator()),
                new EtapaFcValidator(),
                new PlanoEstruturaReparador(new SimpleMeterRegistry()),
                new PaceValidator(),
                new SimpleMeterRegistry());
    }

    /** Espelha o que {@code PlanoLlmValidator#contexto} monta: zonas só com dado fisiológico. */
    private static ContextoNormalizacao contexto(Atleta atleta) {
        List<ZonaFC> zonas = (atleta.getFcLimiar() != null || atleta.getFcMaxima() != null) ? ZONAS_FC_160 : null;
        return new ContextoNormalizacao(atleta, atleta.getId(), zonas, Map.of(), Map.of());
    }

    private static Atleta atleta(boolean comFc) {
        var builder = Atleta.builder()
                .id(ATLETA_ID)
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .paceLimiar(BigDecimal.valueOf(5.0));
        if (comFc) {
            builder.fcLimiar(160).fcMaxima(190);
        }
        return builder.build();
    }

    // ======================================================================================
    // Fixtures de entrada (o que a LLM "gerou")
    // ======================================================================================

    private static TreinoPlanejadoLlmDto entradaIntervalado() {
        return new TreinoPlanejadoLlmDto("SEGUNDA", "INTERVALADO", "150-160 bpm", 60, 1.1, 8,
                "Desenvolver VO2max", "45:00", 5.3, "4:30-5:00/km",
                List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento leve Z1-Z2", 10, 1.5, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(2, "INTERVALADO", "Tiro 1 - Z5", 4, 1.0, "160-170 bpm", 1, "4:00-4:15/km"),
                        new EtapaTreinoLlmDto(3, "RECUPERACAO", "Recuperação 1 - trote Z2", 2, 0.4, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(4, "INTERVALADO", "Tiro 2 - Z5", 4, 1.0, "160-170 bpm", 1, "4:00-4:15/km"),
                        new EtapaTreinoLlmDto(5, "RECUPERACAO", "Recuperação 2 - trote Z2", 2, 0.4, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(6, "DESAQUECIMENTO", "Desaquecimento leve Z1", 5, 1.0, "120-136 bpm", 1, null)));
    }

    private static TreinoPlanejadoLlmDto entradaLongo() {
        return new TreinoPlanejadoLlmDto("SABADO", "LONGO", "136-150 bpm", 90, 0.85, 6,
                "Construir base aeróbica", "75:00", 15.0, "5:30-6:00/km",
                List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento leve Z1-Z2", 10, 1.5, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(2, "PRINCIPAL", "Longo contínuo Z2-Z3", 60, 12.0, "136-150 bpm", 1, "5:30-6:00/km"),
                        new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Desaquecimento leve Z1", 5, 1.5, "120-136 bpm", 1, null)));
    }

    private static TreinoPlanejadoLlmDto entradaRegenerativo() {
        return new TreinoPlanejadoLlmDto("QUARTA", "REGENERATIVO", "115-130 bpm", 25, 0.6, 3,
                "Recuperação ativa", "35:00", 5.0, "6:30-7:00/km",
                List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento muito leve", 5, 0.7, "115-130 bpm", 1, null),
                        new EtapaTreinoLlmDto(2, "PRINCIPAL", "Trote regenerativo Z1", 25, 3.6, "115-130 bpm", 1, "6:30-7:00/km"),
                        new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Desaquecimento leve", 5, 0.7, "115-130 bpm", 1, null)));
    }

    private static TreinoPlanejadoLlmDto entradaFartlek() {
        return new TreinoPlanejadoLlmDto("QUINTA", "FARTLEK", "136-160 bpm", 55, 1.0, 7,
                "Variação de ritmo", "40:00", 7.0, "5:00-5:45/km",
                List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 10, 1.5, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(2, "PRINCIPAL", "4x (1min Z2 + 2min Z1)", 12, 2.0, "136-160 bpm", 1, null),
                        new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Caminhada", 10, 1.3, "120-136 bpm", 1, null)));
    }

    private static TreinoPlanejadoLlmDto entradaFacil() {
        return new TreinoPlanejadoLlmDto("TERCA", "FACIL", "130-145 bpm", 40, 0.7, 4,
                "Rodagem fácil", "40:00", 6.5, "6:00-6:30/km",
                List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 5, 0.8, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(2, "PRINCIPAL", "Rodagem confortável", 30, 5.0, "130-145 bpm", 1, "6:00-6:30/km"),
                        new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Caminhada", 5, 0.7, "120-136 bpm", 1, null)));
    }

    private static TreinoPlanejadoLlmDto entradaIntervaladoComprimido() {
        return new TreinoPlanejadoLlmDto("SEGUNDA", "INTERVALADO", "150-160 bpm", 60, 1.1, 8,
                "Desenvolver VO2max", "45:00", 7.0, "4:30-5:00/km",
                List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento leve", 10, 1.5, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(2, "INTERVALADO", "5x800m Z4 com 400m trote", 20, 4.0, "150-160 bpm", 1, "4:00-4:15/km"),
                        new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Desaquecimento leve", 10, 1.5, "120-136 bpm", 1, null)));
    }

    private static TreinoPlanejadoLlmDto entradaRegenerativoSoPrincipal() {
        return new TreinoPlanejadoLlmDto("QUARTA", "REGENERATIVO", "115-130 bpm", 25, 0.6, 3,
                "Recuperação ativa", "30:00", 4.0, "6:30-7:00/km",
                List.of(new EtapaTreinoLlmDto(1, "PRINCIPAL", "Trote regenerativo Z1", 30, 4.0, "115-130 bpm", 1, "6:30-7:00/km")));
    }

    private static TreinoPlanejadoLlmDto entradaRepeticoesNull() {
        return new TreinoPlanejadoLlmDto("SABADO", "LONGO", "136-150 bpm", 90, 0.85, 6,
                "Base", "75:00", 15.0, "5:30-6:00/km",
                List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento leve", 10, 1.5, "120-136 bpm", null, null),
                        new EtapaTreinoLlmDto(2, "PRINCIPAL", "Longo contínuo", 60, 12.0, "136-150 bpm", null, "5:30-6:00/km"),
                        new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Desaquecimento leve", 5, 1.5, "120-136 bpm", null, null)));
    }

    // aquec/desaq com 1.67km = o que corrigirDistanciasEtapasTemporais deriva de 10min × paceZ2 6.0
    // (evita surpresa de soma antes da normalização, ver design.md)
    private static TreinoPlanejadoLlmDto entradaPadding4Etapas() {
        return new TreinoPlanejadoLlmDto("TERCA", "INTERVALADO", "150-160 bpm", 60, 8.0, 6, "VO2max", "50:00", 8.0, "5:00-5:15/km",
                List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 10, 1.67, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(1, "INTERVALADO", "Intervalo Z5", 4, 0.8, "90-95% FCmax", 1, null),
                        new EtapaTreinoLlmDto(1, "RECUPERACAO", "Recuperação trote", 2, 0.3, "60-70% FCmax", 1, null),
                        new EtapaTreinoLlmDto(1, "DESAQUECIMENTO", "Caminhada", 10, 1.67, "120-136 bpm", 1, null)));
    }

    // gap +0.57km distribuído em 4 tiros (0.8→~0.94km); pace 12 min/km → duracaoMin 11 > 10
    private static TreinoPlanejadoLlmDto entradaTiroPassaDe10min() {
        var tiro = new EtapaTreinoLlmDto(1, "INTERVALADO", "Intervalo Z5", 4, 0.8, "90-95% FCmax", 1, "12:00-12:00/km");
        var rec = new EtapaTreinoLlmDto(1, "RECUPERACAO", "Recuperação trote", 2, 0.3, "60-70% FCmax", 1, null);
        return new TreinoPlanejadoLlmDto("TERCA", "INTERVALADO", "150-160 bpm", 60, 8.0, 6, "VO2max", "50:00", 7.84, "5:00-5:15/km",
                List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 10, 1.67, "120-136 bpm", 1, null),
                        tiro, rec, tiro, rec, tiro, rec, tiro,
                        new EtapaTreinoLlmDto(1, "DESAQUECIMENTO", "Caminhada", 10, 1.67, "120-136 bpm", 1, null)));
    }

    private static TreinoPlanejadoLlmDto entradaTiroDuracaoNull() {
        var tiro = new EtapaTreinoLlmDto(1, "INTERVALADO", "Intervalo Z5", null, 0.8, "90-95% FCmax", 1, null);
        var rec = new EtapaTreinoLlmDto(1, "RECUPERACAO", "Recuperação trote", 2, 0.3, "60-70% FCmax", 1, null);
        return new TreinoPlanejadoLlmDto("TERCA", "INTERVALADO", "150-160 bpm", 60, 8.0, 6, "VO2max", "50:00", 7.44, "5:00-5:15/km",
                List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 10, 1.67, "120-136 bpm", 1, null),
                        tiro, rec, tiro, rec, tiro, rec, tiro,
                        new EtapaTreinoLlmDto(1, "DESAQUECIMENTO", "Caminhada", 10, 1.67, "120-136 bpm", 1, null)));
    }

    private static TreinoPlanejadoLlmDto entradaRecuperacaoSemTiro() {
        var tiro = new EtapaTreinoLlmDto(1, "INTERVALADO", "Intervalo Z5", 4, 0.8, "90-95% FCmax", 1, null);
        var rec = new EtapaTreinoLlmDto(1, "RECUPERACAO", "Recuperação trote", 2, 0.3, "60-70% FCmax", 1, null);
        return new TreinoPlanejadoLlmDto("TERCA", "INTERVALADO", "150-160 bpm", 60, 8.0, 6, "VO2max", "50:00", 6.0, "5:00-5:15/km",
                List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 10, 1.67, "120-136 bpm", 1, null),
                        rec, tiro, tiro, rec,
                        new EtapaTreinoLlmDto(1, "DESAQUECIMENTO", "Caminhada", 10, 1.67, "120-136 bpm", 1, null)));
    }
}
