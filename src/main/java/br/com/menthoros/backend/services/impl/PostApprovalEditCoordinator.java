package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.WorkoutEditReason;
import br.com.menthoros.backend.events.WorkoutEditedAfterApprovalEvent;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.GarminConnectionService;
import br.com.menthoros.backend.services.helper.ApprovedWorkoutEditPolicy;
import br.com.menthoros.backend.services.helper.AtletaHojeResolver;
import br.com.menthoros.backend.services.helper.IntervalsIcuWorkoutConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Caminho da edição de treino em plano {@code APROVADO} (coach-editar-treino-aprovado, D1/D2),
 * separado do {@link TreinoPlanejadoServiceImpl} para não crescer o service. O service chama
 * {@link #checkBeforePatch} antes de aplicar o patch e {@link #applyAfterPatch} depois, na mesma
 * transação: uma recusa lançada aqui faz rollback do patch.
 *
 * <p><b>Tenant-aware:</b> YES — usa o {@code tenantId} do treino, já buscado por tenant pelo service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PostApprovalEditCoordinator {

    private static final List<FonteDados> CANAIS = List.of(FonteDados.INTERVALS_ICU, FonteDados.GARMIN);

    private final AtletaHojeResolver atletaHojeResolver;
    private final ApprovedWorkoutEditPolicy policy;
    private final IntegracaoExternaRepository integracaoExternaRepository;
    private final GarminConnectionService garminConnectionService;
    private final IntervalsIcuWorkoutConverter converter;
    private final ApplicationEventPublisher eventPublisher;
    private final PostApprovalEditRecorder recorder;
    private final Clock clock;

    /**
     * Recusa a edição antes de qualquer mutação: regra de editabilidade (422) e envio ao relógio em
     * andamento (409).
     *
     * <p><b>Idempotente:</b> YES — só leitura. <b>Side Effects:</b> NONE.
     *
     * @return o snapshot do treino antes do patch, para o registro da edição (D7)
     */
    String checkBeforePatch(PlanoSemanal plano, TreinoPlanejado treino) {
        boolean hasWatch = hasWatch(plano.getAtleta().getId(), treino.getTenantId());
        policy.avaliar(treino, atletaHojeResolver.hojeDe(plano.getAtleta()), hasWatch)
                .ifPresent(recusa -> {
                    throw new DomainRuleViolationException(recusa.message());
                });

        // Só SINCRONIZANDO na janela: AGUARDANDO_RETRY é a própria edição anterior esperando o push,
        // e o próximo claim recarrega o conteúdo novo (D1, regra 3).
        if (treino.getStatusSincronizacao() == StatusSincronizacao.SINCRONIZANDO
                && treino.envioEmAndamento(LocalDateTime.now(clock))) {
            throw new DomainConflictException("Envio ao relógio em andamento; tente em alguns segundos.");
        }
        return recorder.snapshot(treino);
    }

    /**
     * "Atleta com relógio" da regra D1: existe conexão de canal, ativa ou revogada — conexão caída não
     * tira o treino antigo do aparelho. Ponto único para o PATCH e para o {@code editable} do perfil.
     *
     * <p><b>Idempotente:</b> YES — só leitura. <b>Tenant-aware:</b> YES.
     */
    boolean hasWatch(UUID atletaId, UUID tenantId) {
        return integracaoExternaRepository
                .findFirstByAtletaIdAndPlataformaInAndTenantIdOrderByAtivoDescAtualizadoEmDesc(
                        atletaId, CANAIS, tenantId)
                .isPresent();
    }

    /**
     * Depois do patch: exige resultado exportável (422), marca a edição, grava o registro (D7) e,
     * com canal ativo, prepara o reenvio e publica o evento do push só deste treino.
     *
     * <p><b>Idempotente:</b> NO — um registro e um push a cada chamada.
     * <b>Side Effects:</b> mutação do treino (o service persiste), insert do registro e evento após
     * o commit.
     */
    void applyAfterPatch(PlanoSemanal plano, TreinoPlanejado treino, String snapshotBefore,
                         @Nullable WorkoutEditReason reason) {
        if (converter.converter(treino).isEmpty()) {
            throw new DomainRuleViolationException(
                    "O treino editado precisa ter duração ou distância para ser enviado ao relógio.");
        }

        Instant agora = clock.instant();
        treino.setEditedAfterApprovalAt(agora);
        recorder.record(plano, treino, snapshotBefore, reason, agora);

        UUID atletaId = plano.getAtleta().getId();
        UUID tenantId = treino.getTenantId();
        if (garminConnectionService.conexaoDeCanal(atletaId, tenantId).isEmpty()) {
            log.info("Treino editado após aprovação sem canal: treinoId={}, tenantId={}", treino.getId(), tenantId);
            return;
        }
        treino.reiniciarSincronizacao(LocalDateTime.now(clock));
        eventPublisher.publishEvent(
                new WorkoutEditedAfterApprovalEvent(treino.getId(), plano.getId(), atletaId, tenantId));
        log.info("Treino editado após aprovação, reenvio agendado: treinoId={}, tenantId={}",
                treino.getId(), tenantId);
    }
}
