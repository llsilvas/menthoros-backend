package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.planner.WeekPlanSkeleton;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.ModoGeracaoPlano;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Task 4.3b (add-descanso-explicito-por-fadiga): a resposta "a cobertura comanda esta geração?"
 * precisa ser a MESMA no {@code IaServiceImpl} (que valida) e no {@code PlanGenerationPersister}
 * (que redistribui). Este componente é onde ela mora.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CoberturaSemanalPolicy")
class CoberturaSemanalPolicyTest {

    @Mock
    private RegraGeracaoTreino regraGeracaoTreino;

    @InjectMocks
    private CoberturaSemanalPolicy policy;

    private Atleta atleta;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(policy, "habilitada", true);
        atleta = new Atleta();
        atleta.setDiasDisponiveis(List.of(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUINTA, DiaSemana.SABADO));
    }

    @Nested
    @DisplayName("ativa")
    class Ativa {

        @Test
        @DisplayName("PROXIMA_SEMANA sem skeleton: ativa, sobre os dias configurados")
        void proximaSemanaSemSkeleton() {
            assertThat(policy.ativa(atleta, ModoGeracaoPlano.PROXIMA_SEMANA, null)).isTrue();
            verifyNoInteractions(regraGeracaoTreino);
        }

        @Test
        @DisplayName("SEMANA_ATUAL sem skeleton: ativa quando ainda resta dia disponível")
        void semanaAtualComDiasRestantes() {
            when(regraGeracaoTreino.filtrarDiasDisponiveis(any(), any(), eq(ModoGeracaoPlano.SEMANA_ATUAL)))
                    .thenReturn(List.of(DiaSemana.QUINTA, DiaSemana.SABADO));

            assertThat(policy.ativa(atleta, ModoGeracaoPlano.SEMANA_ATUAL, null)).isTrue();
        }

        @Test
        @DisplayName("SEMANA_ATUAL sem nenhum dia restante: não ativa — não há o que cobrir")
        void semanaAtualSemDiasRestantes() {
            when(regraGeracaoTreino.filtrarDiasDisponiveis(any(), any(), eq(ModoGeracaoPlano.SEMANA_ATUAL)))
                    .thenReturn(List.of());

            assertThat(policy.ativa(atleta, ModoGeracaoPlano.SEMANA_ATUAL, null)).isFalse();
        }

        @Test
        @DisplayName("com skeleton do planner: não ativa — ali o planner é dono da frequência")
        void comSkeletonNaoAtiva() {
            WeekPlanSkeleton skeleton = org.mockito.Mockito.mock(WeekPlanSkeleton.class);

            assertThat(policy.ativa(atleta, ModoGeracaoPlano.PROXIMA_SEMANA, skeleton)).isFalse();
            verifyNoInteractions(regraGeracaoTreino);
        }

        @Test
        @DisplayName("kill-switch desligado: não ativa em nenhum modo")
        void killSwitchDesligado() {
            ReflectionTestUtils.setField(policy, "habilitada", false);

            assertThat(policy.ativa(atleta, ModoGeracaoPlano.PROXIMA_SEMANA, null)).isFalse();
            assertThat(policy.ativa(atleta, ModoGeracaoPlano.SEMANA_ATUAL, null)).isFalse();
            verifyNoInteractions(regraGeracaoTreino);
        }

        @Test
        @DisplayName("atleta sem dia configurado: não ativa")
        void semDiasConfigurados() {
            atleta.setDiasDisponiveis(List.of());

            assertThat(policy.ativa(atleta, ModoGeracaoPlano.PROXIMA_SEMANA, null)).isFalse();
        }
    }

    @Nested
    @DisplayName("diasEfetivos")
    class DiasEfetivos {

        @Test
        @DisplayName("PROXIMA_SEMANA devolve null — o prompt materializa a lista")
        void proximaSemanaNull() {
            assertThat(policy.diasEfetivos(atleta, ModoGeracaoPlano.PROXIMA_SEMANA)).isNull();
        }

        @Test
        @DisplayName("SEMANA_ATUAL devolve só os dias que ainda não passaram")
        void semanaAtualFiltra() {
            when(regraGeracaoTreino.filtrarDiasDisponiveis(eq(atleta.getDiasDisponiveis()), any(LocalDate.class),
                    eq(ModoGeracaoPlano.SEMANA_ATUAL))).thenReturn(List.of(DiaSemana.QUINTA, DiaSemana.SABADO));

            assertThat(policy.diasEfetivos(atleta, ModoGeracaoPlano.SEMANA_ATUAL))
                    .containsExactly(DiaSemana.QUINTA, DiaSemana.SABADO);
        }

        @Test
        @DisplayName("diasACobrir cai nos dias configurados quando não há filtro (PROXIMA_SEMANA)")
        void diasACobrirProximaSemana() {
            assertThat(policy.diasACobrir(atleta, ModoGeracaoPlano.PROXIMA_SEMANA))
                    .isEqualTo(atleta.getDiasDisponiveis());
        }
    }
}
