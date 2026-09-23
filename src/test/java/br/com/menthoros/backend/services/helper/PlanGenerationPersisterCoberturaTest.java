package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.ModoGeracaoPlano;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.services.plano.ProvaNoPlanoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task 4.3b (add-descanso-explicito-por-fadiga, Decisão 6): com a cobertura validada, a
 * redistribuição NÃO roda.
 *
 * <p>Regressão da geração real de 22/09 20:49: o check-in do atleta dizia DESCANSAR, a LLM prescreveu
 * descanso na QUINTA e treino no SÁBADO, a cobertura aprovou — e então a redistribuição mudou o
 * CONTINUO de SÁBADO para QUINTA. O descanso caiu (o dia tinha treino) e o sábado ficou vazio: o
 * inverso exato da prescrição, num plano que foi auto-aprovado e entregue ao atleta.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlanGenerationPersister — redistribuição x cobertura")
class PlanGenerationPersisterCoberturaTest {

    @Mock
    private RedistribuicaoTreinoHelper redistribuicaoHelper;
    @Mock
    private ProvaNoPlanoService provaNoPlanoService;
    @Mock
    private CoberturaSemanalPolicy coberturaSemanalPolicy;

    @InjectMocks
    private PlanGenerationPersister persister;

    private Atleta atleta;
    private PlanGenerationPersister.PeriodoPlano periodo;

    @BeforeEach
    void setUp() {
        atleta = new Atleta();
        atleta.setDiasDisponiveis(List.of(DiaSemana.QUINTA, DiaSemana.SABADO));
        periodo = new PlanGenerationPersister.PeriodoPlano(LocalDate.of(2026, 9, 21));
    }

    @Test
    @DisplayName("cobertura ativa em SEMANA_ATUAL: os treinos vão para o plano como vieram")
    void coberturaAtivaNaoRedistribui() {
        List<TreinoPlanejadoLlmDto> daLlm = List.of(treino("SABADO"));
        when(provaNoPlanoService.garantirProvasNaSemana(anyList(), any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));

        var resultado = persister.obterTreinosParaPlano(daLlm, atleta, periodo,
                ModoGeracaoPlano.SEMANA_ATUAL, DiaSemana.SABADO, Map.of(), true);

        assertThat(resultado).extracting(TreinoPlanejadoLlmDto::diaSemana).containsExactly("SABADO");
        verify(redistribuicaoHelper, never()).redistribuirTreinos(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("cobertura inativa em SEMANA_ATUAL: redistribuição continua rodando (comportamento legado)")
    void coberturaInativaRedistribui() {
        List<TreinoPlanejadoLlmDto> daLlm = List.of(treino("SABADO"));
        when(redistribuicaoHelper.redistribuirTreinos(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(treino("QUINTA")));
        when(provaNoPlanoService.garantirProvasNaSemana(anyList(), any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));

        var resultado = persister.obterTreinosParaPlano(daLlm, atleta, periodo,
                ModoGeracaoPlano.SEMANA_ATUAL, DiaSemana.SABADO, Map.of(), false);

        assertThat(resultado).extracting(TreinoPlanejadoLlmDto::diaSemana).containsExactly("QUINTA");
    }

    @Test
    @DisplayName("a garantia da prova continua rodando com a cobertura ativa")
    void provaAindaRoda() {
        List<TreinoPlanejadoLlmDto> daLlm = List.of(treino("SABADO"));
        when(provaNoPlanoService.garantirProvasNaSemana(anyList(), any(), any(), any()))
                .thenReturn(List.of(treino("SABADO"), provaEm("QUINTA")));

        var resultado = persister.obterTreinosParaPlano(daLlm, atleta, periodo,
                ModoGeracaoPlano.SEMANA_ATUAL, DiaSemana.SABADO, Map.of(), true);

        assertThat(resultado).extracting(TreinoPlanejadoLlmDto::tipoTreino)
                .containsExactly("CONTINUO", TipoTreino.PROVA.name());
    }

    private static TreinoPlanejadoLlmDto treino(String dia) {
        return new TreinoPlanejadoLlmDto(dia, "CONTINUO", null, null, null, null, null, null, null, null, List.of());
    }

    private static TreinoPlanejadoLlmDto provaEm(String dia) {
        return new TreinoPlanejadoLlmDto(dia, TipoTreino.PROVA.name(), null, null, null, null, null, null, null, null,
                List.of());
    }
}
