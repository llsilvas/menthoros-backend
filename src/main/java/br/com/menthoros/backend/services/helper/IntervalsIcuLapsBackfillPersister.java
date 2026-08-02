package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Grava as etapas de UM treino, em transação própria e curta — colaborador transacional do
 * backfill (D9).
 *
 * <p>Uma transação por treino, e não uma para o lote inteiro: a chamada HTTP acontece FORA daqui,
 * no orquestrador, e uma falha em um treino não pode arrastar os já corrigidos.
 *
 * <p>Idempotent: YES — só é chamado para treinos sem etapas; reexecutar não duplica porque o
 * candidato deixa de existir.
 * <p>Side Effects: Database insert das etapas (por cascade). O summary do treino NÃO é alterado.
 * <p>Tenant-aware: YES — o treino é recarregado por {@code (id, tenantId)}.
 *
 * <p><b>Janela de corrida aceita:</b> o guard "já tem etapas" é check-then-act sem lock. Dois
 * disparos simultâneos do backfill para o mesmo atleta poderiam duplicar etapas sob
 * {@code READ COMMITTED}. Aceito: é ação manual do coach sobre um passivo finito, e o custo de um
 * lock ou de uma constraint de unicidade não se paga para essa probabilidade. Se o backfill virar
 * job automático, fechar a janela antes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntervalsIcuLapsBackfillPersister {

    private final TreinoRealizadoRepository treinoRealizadoRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void gravarEtapas(UUID treinoId, List<EtapaRealizada> etapas, UUID tenantId) {
        if (etapas == null || etapas.isEmpty()) {
            return;
        }

        TreinoRealizado treino = treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId)
                .orElse(null);
        if (treino == null) {
            log.warn("Backfill: treino {} não encontrado no tenant {} — ignorado", treinoId, tenantId);
            return;
        }
        if (!treino.getEtapasRealizadas().isEmpty()) {
            log.debug("Backfill: treino {} já tem etapas — ignorado", treinoId);
            return;
        }

        for (EtapaRealizada etapa : etapas) {
            etapa.setTreinoRealizado(treino);
            treino.getEtapasRealizadas().add(etapa);
        }
        treinoRealizadoRepository.save(treino);
    }
}
