package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.output.AtletaPerfilCoachOutputDto.ConexaoCanalDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.helper.ApprovedWorkoutEditContext.Evaluation;
import br.com.menthoros.backend.services.helper.ApprovedWorkoutEditPolicy.EditRejection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovedWorkoutEditContextTest {

    // 15h UTC = 12h em São Paulo, fuso padrão do atleta sem timezone
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 5);
    private static final List<FonteDados> CANAIS = List.of(FonteDados.INTERVALS_ICU, FonteDados.GARMIN);

    @Mock private IntegracaoExternaRepository integracaoExternaRepository;

    private ApprovedWorkoutEditContext context;
    private UUID tenantId;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        context = new ApprovedWorkoutEditContext(
                new AtletaHojeResolver(CLOCK), new ApprovedWorkoutEditPolicy(), integracaoExternaRepository);
        tenantId = UUID.randomUUID();
        atleta = new Atleta();
        atleta.setId(UUID.randomUUID());
    }

    @Nested
    @DisplayName("resolve")
    class Resolve {

        @Test
        @DisplayName("sem conexão: hasWatch false e conexaoCanal nulo")
        void semConexao() {
            semCanal();

            ApprovedWorkoutEditContext.Resolved resolved = context.resolve(atleta, tenantId);

            assertThat(resolved.hoje()).isEqualTo(HOJE);
            assertThat(resolved.hasWatch()).isFalse();
            assertThat(resolved.conexaoCanal()).isNull();
        }

        @Test
        @DisplayName("Garmin ativo: hasWatch true e conexaoCanal com o mapeamento do perfil")
        void garminAtivo() {
            canal(integracao(FonteDados.GARMIN, true, "WORKOUT_IMPORT,ACTIVITY_EXPORT"));

            ApprovedWorkoutEditContext.Resolved resolved = context.resolve(atleta, tenantId);

            assertThat(resolved.hasWatch()).isTrue();
            assertThat(resolved.conexaoCanal())
                    .isEqualTo(new ConexaoCanalDto(FonteDados.GARMIN, true, true, true, null, null));
        }

        @Test
        @DisplayName("intervals.icu usa os scopes do intervals")
        void intervalsAtivo() {
            canal(integracao(FonteDados.INTERVALS_ICU, true, "CALENDAR:WRITE"));

            ConexaoCanalDto canal = context.resolve(atleta, tenantId).conexaoCanal();

            assertThat(canal).isEqualTo(new ConexaoCanalDto(FonteDados.INTERVALS_ICU, true, true, false, null, null));
        }

        @Test
        @DisplayName("conexão revogada conta como relógio e sai com ativo=false")
        void revogada() {
            canal(integracao(FonteDados.GARMIN, false, null));

            ApprovedWorkoutEditContext.Resolved resolved = context.resolve(atleta, tenantId);

            assertThat(resolved.hasWatch()).isTrue();
            assertThat(resolved.conexaoCanal().ativo()).isFalse();
        }

        @Test
        @DisplayName("uma consulta de integração para N planos e treinos")
        void umaConsultaParaNTreinos() {
            semCanal();
            ApprovedWorkoutEditContext.Resolved resolved = context.resolve(atleta, tenantId);
            PlanoSemanal plano = plano(PlanoReviewStatus.APROVADO, PlanoStatus.EM_ANDAMENTO);

            for (int i = 0; i < 5; i++) {
                resolved.evaluate(plano, treino(TreinoExecucaoStatus.PENDENTE, HOJE.plusDays(i)));
            }

            verify(integracaoExternaRepository, times(1))
                    .findFirstByAtletaIdAndPlataformaInAndTenantIdOrderByAtivoDescAtualizadoEmDesc(
                            atleta.getId(), CANAIS, tenantId);
        }
    }

    @Nested
    @DisplayName("evaluate")
    class Evaluate {

        @Test
        @DisplayName("plano em revisão: não se aplica (campos omitidos)")
        void revisaoNaoSeAplica() {
            semCanal();
            Evaluation e = context.resolve(atleta, tenantId).evaluate(
                    plano(PlanoReviewStatus.AGUARDANDO_REVISAO, PlanoStatus.EM_ANDAMENTO),
                    treino(TreinoExecucaoStatus.PENDENTE, HOJE.plusDays(1)));

            assertThat(e).isEqualTo(Evaluation.NOT_APPLICABLE);
            assertThat(e.editable()).isNull();
            assertThat(e.editRejection()).isNull();
        }

        @Test
        @DisplayName("plano APROVADO e CONCLUIDO: não se aplica, mesmo com treino futuro pendente")
        void concluidoNaoSeAplica() {
            semCanal();
            Evaluation e = context.resolve(atleta, tenantId).evaluate(
                    plano(PlanoReviewStatus.APROVADO, PlanoStatus.CONCLUIDO),
                    treino(TreinoExecucaoStatus.PENDENTE, HOJE.plusDays(1)));

            assertThat(e).isEqualTo(Evaluation.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("plano APROVADO em andamento, treino pendente de amanhã: editável")
        void editavel() {
            semCanal();
            Evaluation e = aprovado(treino(TreinoExecucaoStatus.PENDENTE, HOJE.plusDays(1)));

            assertThat(e.editable()).isTrue();
            assertThat(e.editRejection()).isNull();
        }

        @Test
        @DisplayName("treino de ontem: PAST_DAY")
        void ontem() {
            semCanal();
            Evaluation e = aprovado(treino(TreinoExecucaoStatus.PENDENTE, HOJE.minusDays(1)));

            assertThat(e.editable()).isFalse();
            assertThat(e.editRejection()).isEqualTo("PAST_DAY");
        }

        @Test
        @DisplayName("treino realizado: NOT_PENDING")
        void realizado() {
            semCanal();
            Evaluation e = aprovado(treino(TreinoExecucaoStatus.REALIZADO, HOJE.plusDays(1)));

            assertThat(e.editable()).isFalse();
            assertThat(e.editRejection()).isEqualTo("NOT_PENDING");
        }

        @Test
        @DisplayName("treino de hoje com conexão revogada: TODAY_ON_WATCH")
        void hojeComRevogada() {
            canal(integracao(FonteDados.GARMIN, false, null));
            Evaluation e = aprovado(treino(TreinoExecucaoStatus.PENDENTE, HOJE));

            assertThat(e.editable()).isFalse();
            assertThat(e.editRejection()).isEqualTo("TODAY_ON_WATCH");
        }

        @Test
        @DisplayName("treino de hoje sem relógio: editável")
        void hojeSemRelogio() {
            semCanal();
            assertThat(aprovado(treino(TreinoExecucaoStatus.PENDENTE, HOJE)).editable()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = PlanoReviewStatus.class, names = "APROVADO", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("qualquer review diferente de APROVADO: não se aplica")
        void naoAprovadoNaoSeAplica(PlanoReviewStatus review) {
            semCanal();
            Evaluation e = context.resolve(atleta, tenantId).evaluate(
                    plano(review, PlanoStatus.EM_ANDAMENTO), treino(TreinoExecucaoStatus.PENDENTE, HOJE.minusDays(3)));

            assertThat(e).isEqualTo(Evaluation.NOT_APPLICABLE);
        }

        private Evaluation aprovado(TreinoPlanejado treino) {
            return context.resolve(atleta, tenantId)
                    .evaluate(plano(PlanoReviewStatus.APROVADO, PlanoStatus.EM_ANDAMENTO), treino);
        }
    }

    @Nested
    @DisplayName("recusaDaPolicySemGuardaDePlano")
    class RecusaDaPolicySemGuardaDePlano {

        @Test
        @DisplayName("aplica só a policy, com o hasWatch informado e sem consultar integração")
        void soPolicy() {
            ApprovedWorkoutEditContext.Resolved resolved = context.forHasWatch(atleta, true);

            assertThat(resolved.recusaDaPolicySemGuardaDePlano(treino(TreinoExecucaoStatus.PENDENTE, HOJE)))
                    .contains(EditRejection.TODAY_ON_WATCH);
            assertThat(resolved.recusaDaPolicySemGuardaDePlano(treino(TreinoExecucaoStatus.PENDENTE, HOJE.plusDays(1))))
                    .isEmpty();
            org.mockito.Mockito.verifyNoInteractions(integracaoExternaRepository);
        }
    }

    @Nested
    @DisplayName("conexaoCanal")
    class ConexaoCanal {

        @Test
        @DisplayName("mesmo mapeamento do resolve, com o motivo do erro")
        void mapeamento() {
            IntegracaoExterna garmin = integracao(FonteDados.GARMIN, false, "WORKOUT_IMPORT");
            garmin.setLastSyncError("Autorização revogada");
            canal(garmin);

            assertThat(context.conexaoCanal(atleta.getId(), tenantId))
                    .isEqualTo(new ConexaoCanalDto(FonteDados.GARMIN, false, true, false, "Autorização revogada", null));
        }

        @Test
        @DisplayName("sem conexão: nulo")
        void semConexao() {
            semCanal();
            assertThat(context.conexaoCanal(atleta.getId(), tenantId)).isNull();
        }
    }

    private void semCanal() {
        canal(null);
    }

    private void canal(IntegracaoExterna integracao) {
        when(integracaoExternaRepository.findFirstByAtletaIdAndPlataformaInAndTenantIdOrderByAtivoDescAtualizadoEmDesc(
                atleta.getId(), CANAIS, tenantId)).thenReturn(Optional.ofNullable(integracao));
    }

    private static IntegracaoExterna integracao(FonteDados plataforma, boolean ativo, String scopes) {
        IntegracaoExterna i = new IntegracaoExterna();
        i.setPlataforma(plataforma);
        i.setAtivo(ativo);
        i.setScopes(scopes);
        return i;
    }

    private static PlanoSemanal plano(PlanoReviewStatus review, PlanoStatus status) {
        PlanoSemanal plano = new PlanoSemanal();
        plano.setReviewStatus(review);
        plano.setStatus(status);
        return plano;
    }

    private static TreinoPlanejado treino(TreinoExecucaoStatus status, LocalDate data) {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setStatusTreino(status);
        treino.setDataTreino(data);
        return treino;
    }
}
