package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.ai.ledger.Violacao;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.LLMException;
import br.com.menthoros.backend.exception.PlanoNaoConformeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PlanoResilienceService")
class PlanoResilienceServiceTest {

    private MeterRegistry registry;
    private PlanoResilienceService service;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        service = new PlanoResilienceService(registry);
    }

    private static PlanoSemanalLlmDto plano() {
        return new PlanoSemanalLlmDto(0.0, 0.0, null, null, null, null, List.of(), List.of());
    }

    private static PlanoResilienceService.ChamadaLlm chamada() {
        return new PlanoResilienceService.ChamadaLlm(plano(), "{}");
    }

    private double contador(String nome) {
        var c = registry.find(nome).counter();
        return c == null ? 0.0 : c.count();
    }

    @Nested
    @DisplayName("deadline total")
    class DeadlineTotal {

        @Test
        @DisplayName("1ª tentativa que estoura o orçamento não inicia a 2ª (CA4)")
        void primeiraLentaNaoRetenta() {
            // Deadline curto para o teste; em produção são 100s contra o teto de 120s da rota.
            PlanoResilienceService comDeadlineCurto =
                    new PlanoResilienceService(registry, java.time.Duration.ofMillis(50));
            List<String> chamadas = new ArrayList<>();

            Function<PlanoResilienceService.Tentativa, PlanoResilienceService.ChamadaLlm> gerarLento = t -> {
                chamadas.add(t.promptOriginal());
                try {
                    Thread.sleep(120);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return chamada();
            };
            Function<PlanoSemanalLlmDto, PlanoSemanalLlmDto> rejeita = p -> {
                throw new LLMException("estrutura invalida");
            };

            assertThatThrownBy(() -> comDeadlineCurto.gerarComResiliencia(gerarLento, rejeita, "base"))
                    .isInstanceOf(DomainRuleViolationException.class);

            assertThat(chamadas).hasSize(1);
            assertThat(contador("plano_deadline_estourado")).isEqualTo(1.0);
            assertThat(contador("plano_retry")).isZero();
        }

        @Test
        @DisplayName("1ª tentativa rápida com rejeição segue retentando — o caso comum não regride")
        void primeiraRapidaAindaRetenta() {
            PlanoResilienceService comDeadlineFolgado =
                    new PlanoResilienceService(registry, java.time.Duration.ofSeconds(30));
            List<String> chamadas = new ArrayList<>();

            Function<PlanoResilienceService.Tentativa, PlanoResilienceService.ChamadaLlm> gerar = t -> {
                chamadas.add(t.promptOriginal());
                return chamada();
            };
            Function<PlanoSemanalLlmDto, PlanoSemanalLlmDto> rejeita = p -> {
                throw new LLMException("estrutura invalida");
            };

            assertThatThrownBy(() -> comDeadlineFolgado.gerarComResiliencia(gerar, rejeita, "base"))
                    .isInstanceOf(DomainRuleViolationException.class);

            assertThat(chamadas).hasSize(2);
            assertThat(contador("plano_retry")).isEqualTo(1.0);
            assertThat(contador("plano_deadline_estourado")).isZero();
        }
    }

    @Nested
    @DisplayName("gerarComResiliencia")
    class GerarComResiliencia {

        @Test
        @DisplayName("sucesso na 1ª tentativa → sem retry")
        void sucessoPrimeira() {
            PlanoSemanalLlmDto p = plano();
            PlanoSemanalLlmDto r = service.gerarComResiliencia(
                    t -> new PlanoResilienceService.ChamadaLlm(p, "{}"), plano -> plano, "base");
            assertThat(r).isSameAs(p);
            assertThat(contador("plano_retry")).isZero();
            assertThat(contador("plano_geracao_falha_final")).isZero();
        }

        @Test
        @DisplayName("falha estrutural → turno de reparo com histórico → sucesso")
        void falhaDepoisSucesso() {
            List<PlanoResilienceService.Tentativa> tentativas = new ArrayList<>();
            Function<PlanoResilienceService.Tentativa, PlanoResilienceService.ChamadaLlm> gerar = t -> {
                tentativas.add(t);
                return new PlanoResilienceService.ChamadaLlm(plano(), "{\"tentativa\":" + t.numero() + "}");
            };
            int[] chamadas = {0};
            Function<PlanoSemanalLlmDto, PlanoSemanalLlmDto> validar = plano -> {
                if (++chamadas[0] == 1) {
                    throw new PlanoNaoConformeException("REGENERATIVO inválido: 2 etapas",
                            List.of(new Violacao("NORMALIZACAO_QUARTA", "REGENERATIVO inválido: 2 etapas")));
                }
                return plano;
            };

            PlanoSemanalLlmDto r = service.gerarComResiliencia(gerar, validar, "base");

            assertThat(r).isNotNull();
            assertThat(tentativas).hasSize(2);
            assertThat(tentativas.get(0).promptOriginal()).isEqualTo("base");
            assertThat(tentativas.get(0).jsonAnterior()).isNull();
            assertThat(tentativas.get(0).violacoesAnteriores()).isEmpty();
            // turno de reparo: prompt original NUNCA reescrito — o histórico vai à parte
            assertThat(tentativas.get(1).promptOriginal()).isEqualTo("base");
            assertThat(tentativas.get(1).jsonAnterior()).isEqualTo("{\"tentativa\":1}");
            assertThat(tentativas.get(1).violacoesAnteriores())
                    .containsExactly(new Violacao("NORMALIZACAO_QUARTA", "REGENERATIVO inválido: 2 etapas"));
            assertThat(contador("plano_retry")).isEqualTo(1.0);
            assertThat(contador("plano_geracao_falha_final")).isZero();
        }

        @Test
        @DisplayName("N violações do compliance chegam completas à 2ª tentativa, não só a primeira")
        void nViolacoesChegamCompletas() {
            List<Violacao> violacoes = List.of(
                    new Violacao("COMPLIANCE_TSS", "TSS fora da faixa"),
                    new Violacao("COMPLIANCE_POLARIZACAO", "polarização violada"),
                    new Violacao("COMPLIANCE_ORDEM", "ordem inválida"));
            List<PlanoResilienceService.Tentativa> tentativas = new ArrayList<>();
            int[] chamadas = {0};
            Function<PlanoSemanalLlmDto, PlanoSemanalLlmDto> validar = plano -> {
                if (++chamadas[0] == 1) throw new PlanoNaoConformeException("plano diverge", violacoes);
                return plano;
            };

            service.gerarComResiliencia(t -> { tentativas.add(t); return chamada(); }, validar, "base");

            assertThat(tentativas.get(1).violacoesAnteriores()).hasSize(3).containsExactlyElementsOf(violacoes);
        }

        @Test
        @DisplayName("Tentativa rejeita número menor que 1 e promptOriginal nulo")
        void tentativaInvalida() {
            assertThatThrownBy(() -> new PlanoResilienceService.Tentativa(0, "x", null, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new PlanoResilienceService.Tentativa(1, null, null, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("violacoesAnteriores nula normaliza para lista vazia, não lança")
        void violacoesAnterioresNulaNormaliza() {
            var tentativa = new PlanoResilienceService.Tentativa(1, "base", null, null);
            assertThat(tentativa.violacoesAnteriores()).isEmpty();
        }

        @Test
        @DisplayName("falha estrutural em ambas → DomainRuleViolationException (não 503) + falha final contada")
        void falhaDupla() {
            Function<PlanoSemanalLlmDto, PlanoSemanalLlmDto> validar = plano -> {
                throw new LLMException("falta PRINCIPAL");
            };

            assertThatThrownBy(() -> service.gerarComResiliencia(t -> chamada(), validar, "base"))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("Não foi possível gerar o plano");
            assertThat(contador("plano_geracao_falha_final")).isEqualTo(1.0);
            assertThat(contador("plano_retry")).isEqualTo(1.0); // 1 retry tentado antes de desistir
        }

        @Test
        @DisplayName("falha de geração (infra) propaga — não vira retry nem erro de domínio, e não há 2ª tentativa")
        void falhaGeracaoPropaga() {
            List<PlanoResilienceService.Tentativa> tentativas = new ArrayList<>();
            Function<PlanoResilienceService.Tentativa, PlanoResilienceService.ChamadaLlm> gerar = t -> {
                tentativas.add(t);
                throw new LLMException("LLM indisponível");
            };

            assertThatThrownBy(() -> service.gerarComResiliencia(gerar, plano -> plano, "base"))
                    .isInstanceOf(LLMException.class);
            assertThat(contador("plano_geracao_falha_final")).isZero();
            assertThat(tentativas).hasSize(1); // sem retry: gerar lançou, não validar
        }

        @Test
        @DisplayName("LLMException sem violações tipadas (não PlanoNaoConformeException) vira 1 violação genérica")
        void llmExceptionGenericaViraUmaViolacao() {
            List<PlanoResilienceService.Tentativa> tentativas = new ArrayList<>();
            int[] chamadas = {0};
            Function<PlanoSemanalLlmDto, PlanoSemanalLlmDto> validar = plano -> {
                if (++chamadas[0] == 1) throw new LLMException("motivo qualquer");
                return plano;
            };

            service.gerarComResiliencia(t -> { tentativas.add(t); return chamada(); }, validar, "base");

            assertThat(tentativas.get(1).violacoesAnteriores())
                    .singleElement()
                    .satisfies(v -> {
                        assertThat(v.key()).isEqualTo("LLM_ERRO_ESTRUTURAL");
                        assertThat(v.mensagem()).isEqualTo("motivo qualquer");
                    });
        }
    }

    @Nested
    @DisplayName("orçamento compartilhado por requisição (design 3b)")
    class OrcamentoCompartilhado {

        @Test
        @DisplayName("o mesmo orçamento não ultrapassa 2 gerações somando enforced + fallback")
        void naoUltrapassaTeto() {
            var orcamento = new GenerationBudget(2, java.time.Duration.ofSeconds(30));
            List<String> chamadas = new ArrayList<>();
            Function<PlanoResilienceService.Tentativa, PlanoResilienceService.ChamadaLlm> gerar = p -> {
                chamadas.add(p.promptOriginal());
                return chamada();
            };
            Function<PlanoSemanalLlmDto, PlanoSemanalLlmDto> rejeita = p -> {
                throw new LLMException("x");
            };

            // 1º caminho (enforced): esgota as 2 gerações do orçamento
            assertThatThrownBy(() -> service.gerarComResiliencia(gerar, rejeita, "base", orcamento))
                    .isInstanceOf(DomainRuleViolationException.class);
            assertThat(chamadas).hasSize(2);

            // 2º caminho (fallback) com o MESMO orçamento: já esgotado -> nenhuma geração nova
            assertThatThrownBy(() -> service.gerarComResiliencia(gerar, rejeita, "base", orcamento))
                    .isInstanceOf(DomainRuleViolationException.class);
            assertThat(chamadas).hasSize(2); // não cresceu — fallback não gerou de novo
            assertThat(orcamento.gastas()).isEqualTo(2);
        }

        @Test
        @DisplayName("orçamento já esgotado não inicia nenhuma geração (gerar nunca é chamado)")
        void esgotadoNaoGera() {
            var orcamento = new GenerationBudget(1, java.time.Duration.ofSeconds(30));
            orcamento.tentarDebitar(); // esgota o único slot fora do service
            List<String> chamadas = new ArrayList<>();
            Function<PlanoResilienceService.Tentativa, PlanoResilienceService.ChamadaLlm> gerar = p -> {
                chamadas.add(p.promptOriginal());
                return chamada();
            };

            assertThatThrownBy(() -> service.gerarComResiliencia(gerar, p -> p, "base", orcamento))
                    .isInstanceOf(DomainRuleViolationException.class);
            assertThat(chamadas).isEmpty(); // débito antes da chamada: sem orçamento, não chama o LLM
        }
    }
}
