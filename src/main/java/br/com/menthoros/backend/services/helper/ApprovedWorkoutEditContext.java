package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.output.AtletaPerfilCoachOutputDto.ConexaoCanalDto;
import br.com.menthoros.backend.dto.output.PlanoSemanalOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.helper.ApprovedWorkoutEditPolicy.EditRejection;
import br.com.menthoros.backend.services.impl.GarminConnectionServiceImpl;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Contexto de edição de treino em plano aprovado, resolvido uma vez por requisição
 * (editar-treino-aprovado-planos-dialog, D1): o "hoje" do atleta e a conexão de canal mais recente,
 * numa única consulta — a mesma de {@code PostApprovalEditCoordinator.hasWatch}, então o lápis da
 * leitura não diverge da recusa do PATCH.
 *
 * <p><b>Idempotente:</b> YES — só leitura. <b>Side Effects:</b> NONE. <b>Tenant-aware:</b> YES —
 * consulta de integração filtrada pelo {@code tenantId} recebido.
 */
@Component
@RequiredArgsConstructor
public class ApprovedWorkoutEditContext {

    private static final List<FonteDados> CANAIS = List.of(FonteDados.INTERVALS_ICU, FonteDados.GARMIN);

    private final AtletaHojeResolver atletaHojeResolver;
    private final ApprovedWorkoutEditPolicy policy;
    private final IntegracaoExternaRepository integracaoExternaRepository;

    /**
     * Uma consulta de integração: dela saem {@code hasWatch} (ativa ou revogada) e a {@code conexaoCanal}.
     * O {@code atleta} só dá o fuso do "hoje"; nulo cai no fuso padrão.
     */
    public Resolved resolve(UUID atletaId, @Nullable Atleta atleta, UUID tenantId) {
        Optional<IntegracaoExterna> integracao = buscarIntegracao(atletaId, tenantId);
        return new Resolved(atletaHojeResolver.hojeDe(atleta), integracao.isPresent(),
                integracao.map(ApprovedWorkoutEditContext::toConexaoCanal).orElse(null), policy);
    }

    /** Para quem já tem o {@code hasWatch} (o perfil); não consulta integração e não traz a conexão. */
    public Resolved forHasWatch(Atleta atleta, boolean hasWatch) {
        return new Resolved(atletaHojeResolver.hojeDe(atleta), hasWatch, null, policy);
    }

    /** Conexão de canal mais recente (ativa primeiro), revogada com {@code ativo=false}; nulo se nunca conectou. */
    public @Nullable ConexaoCanalDto conexaoCanal(UUID atletaId, UUID tenantId) {
        return buscarIntegracao(atletaId, tenantId).map(ApprovedWorkoutEditContext::toConexaoCanal).orElse(null);
    }

    private Optional<IntegracaoExterna> buscarIntegracao(UUID atletaId, UUID tenantId) {
        return integracaoExternaRepository
                .findFirstByAtletaIdAndPlataformaInAndTenantIdOrderByAtivoDescAtualizadoEmDesc(atletaId, CANAIS, tenantId);
    }

    private static ConexaoCanalDto toConexaoCanal(IntegracaoExterna i) {
        boolean garmin = i.getPlataforma() == FonteDados.GARMIN;
        Set<String> scopes = i.getScopes() == null ? Set.of()
                : Set.of(i.getScopes().split(GarminConnectionServiceImpl.SEPARADOR_SCOPES));
        return new ConexaoCanalDto(i.getPlataforma(), i.isAtivo(),
                scopes.contains(garmin ? GarminConnectionServiceImpl.WORKOUT_IMPORT : "CALENDAR:WRITE"),
                scopes.contains(garmin ? GarminConnectionServiceImpl.ACTIVITY_EXPORT : "ACTIVITY:READ"),
                i.getLastSyncError(),
                i.getUltimaSincronizacao());
    }

    public record Resolved(LocalDate hoje, boolean hasWatch, @Nullable ConexaoCanalDto conexaoCanal,
                           ApprovedWorkoutEditPolicy policy) {

        /** Listagem do coach: só plano APROVADO e não CONCLUIDO; fora disso, "não se aplica". */
        public Evaluation evaluate(PlanoSemanal plano, TreinoPlanejado treino) {
            if (plano.getReviewStatus() != PlanoReviewStatus.APROVADO || plano.getStatus() == PlanoStatus.CONCLUIDO) {
                return Evaluation.NOT_APPLICABLE;
            }
            return new Evaluation(true, recusaDaPolicySemGuardaDePlano(treino).orElse(null));
        }

        /**
         * Listagem do coach (D3): {@code conexaoCanal} no plano; em plano APROVADO, {@code editedAfterApprovalAt}
         * e, se não CONCLUIDO, {@code editable}/{@code editRejection} por treino, casado com a entidade pelo id.
         * Treino do DTO sem par na entidade fica como veio.
         */
        public PlanoSemanalOutputDto enrich(PlanoSemanalOutputDto dto, PlanoSemanal plano) {
            PlanoSemanalOutputDto.PlanoSemanalOutputDtoBuilder builder = dto.toBuilder().conexaoCanal(conexaoCanal);
            if (plano.getReviewStatus() != PlanoReviewStatus.APROVADO || dto.treinosPlanejados() == null
                    || plano.getTreinosPlanejados() == null) {
                return builder.build();
            }
            Map<UUID, TreinoPlanejado> porId = new HashMap<>();
            plano.getTreinosPlanejados().forEach(t -> porId.put(t.getId(), t));
            return builder.treinosPlanejados(dto.treinosPlanejados().stream()
                            .map(t -> {
                                TreinoPlanejado entidade = t.id() != null ? porId.get(t.id()) : null;
                                if (entidade == null) return t;
                                Evaluation e = evaluate(plano, entidade);
                                return t.toBuilder()
                                        .editable(e.editable())
                                        .editRejection(e.editRejection())
                                        .editedAfterApprovalAt(entidade.getEditedAfterApprovalAt())
                                        .build();
                            })
                            .toList())
                    .build();
        }

        /** Só a policy, sem olhar o plano: o perfil decide a guarda de plano por conta própria. */
        public Optional<EditRejection> recusaDaPolicySemGuardaDePlano(TreinoPlanejado treino) {
            return policy.avaliar(treino, hoje, hasWatch);
        }
    }

    /** {@code applies=false} é "não se aplica" (campos omitidos no JSON), distinto de "editável". */
    public record Evaluation(boolean applies, @Nullable EditRejection rejection) {

        public static final Evaluation NOT_APPLICABLE = new Evaluation(false, null);

        public @Nullable Boolean editable() {
            return applies ? rejection == null : null;
        }

        public @Nullable String editRejection() {
            return rejection != null ? rejection.name() : null;
        }
    }
}
