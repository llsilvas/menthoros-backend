package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.EncerramentoSemanaResultado;
import br.com.menthoros.backend.services.EncerramentoSemanaService;
import br.com.menthoros.backend.services.TreinoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EncerramentoSemanaServiceImpl implements EncerramentoSemanaService {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    private final PlanoSemanalRepository planoSemanalRepository;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final TreinoService treinoService;
    private final Clock clock;

    @Override
    @Transactional
    public EncerramentoSemanaResultado encerrarSemana(UUID planoId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        PlanoSemanal plano = planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Plano semanal não encontrado"));

        LocalDate hoje = hoje();
        List<UUID> perdidos = finalizarPendentes(plano, hoje);

        boolean pronto = plano.getStatus() == PlanoStatus.CONCLUIDO;
        String aviso = (!pronto && hoje.isBefore(plano.getSemanaFim()))
                ? "Semana ainda não terminou; os treinos futuros permanecem pendentes."
                : null;

        log.info("Semana encerrada: plano={}, tenant={}, treinosPerdidos={}, status={}",
                planoId, tenantId, perdidos.size(), plano.getStatus());
        return new EncerramentoSemanaResultado(
                planoId, plano.getStatus(), perdidos.size(), perdidos, pronto, aviso);
    }

    /**
     * Marca como PERDIDO os treinos PENDENTE elegíveis do plano, delegando a marcação
     * unitária a {@link TreinoService#marcarTreinoPerdido(UUID)} (que recalcula o status do plano).
     * Elegível: {@code dataTreino < hoje}, ou {@code dataTreino == hoje} somente no fim da
     * semana ({@code hoje == semanaFim}). Um treino que já não está PENDENTE no momento do
     * processamento (corrida com registro retroativo) é ignorado, sem lançar exceção.
     */
    private List<UUID> finalizarPendentes(PlanoSemanal plano, LocalDate hoje) {
        boolean fimDaSemana = hoje.equals(plano.getSemanaFim());
        List<TreinoPlanejado> pendentes = treinoPlanejadoRepository.findPendentesAteHojeDoPlano(plano.getId(), hoje);
        List<UUID> perdidos = new ArrayList<>();
        for (TreinoPlanejado treino : pendentes) {
            boolean elegivel = treino.getDataTreino().isBefore(hoje)
                    || (treino.getDataTreino().isEqual(hoje) && fimDaSemana);
            if (!elegivel) {
                continue;
            }
            if (treino.getStatusTreino() != TreinoExecucaoStatus.PENDENTE) {
                log.debug("Treino {} ignorado no encerramento: status atual {}",
                        treino.getId(), treino.getStatusTreino());
                continue;
            }
            treinoService.marcarTreinoPerdido(treino.getId());
            perdidos.add(treino.getId());
        }
        return perdidos;
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(ZONA));
    }
}
