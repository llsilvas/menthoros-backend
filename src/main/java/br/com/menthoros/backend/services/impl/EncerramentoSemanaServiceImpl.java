package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.OrigemEncerramento;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.events.SemanaEncerradaEvent;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.EncerramentoLoteResultado;
import br.com.menthoros.backend.services.EncerramentoSemanaResultado;
import br.com.menthoros.backend.services.EncerramentoSemanaService;
import br.com.menthoros.backend.services.FalhaAtleta;
import br.com.menthoros.backend.services.TreinoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final AtletaRepository atletaRepository;
    private final TreinoService treinoService;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Override
    @Transactional
    public EncerramentoSemanaResultado encerrarSemana(UUID planoId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        return encerrarPlanoPorId(planoId, tenantId, hoje(), OrigemEncerramento.ON_DEMAND);
    }

    @Override
    public EncerramentoLoteResultado encerrarSemanaLoteAssessoria() {
        UUID tenantId = TenantContext.getRequiredTenantId();
        LocalDate hoje = hoje();

        // Uma transação por atleta: a falha de um não derruba os demais (que já commitaram).
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        List<EncerramentoSemanaResultado> resultados = new ArrayList<>();
        List<FalhaAtleta> falhas = new ArrayList<>();
        int semPlano = 0;

        for (Atleta atleta : atletaRepository.findAllAtletas(tenantId)) {
            List<PlanoSemanal> planos = planoSemanalRepository.findSemanaCorrente(atleta.getId(), tenantId, hoje);
            if (planos.isEmpty()) {
                semPlano++;
                continue;
            }
            UUID planoId = planos.get(0).getId();
            try {
                EncerramentoSemanaResultado r = tx.execute(status ->
                        encerrarPlanoPorId(planoId, tenantId, hoje, OrigemEncerramento.ON_DEMAND));
                resultados.add(r);
            } catch (Exception e) {
                log.warn("Falha ao encerrar semana do atleta {}: {}", atleta.getId(), e.getMessage());
                falhas.add(new FalhaAtleta(atleta.getId(), e.getMessage()));
            }
        }
        return consolidar(resultados, semPlano, falhas);
    }

    @Override
    @Transactional(readOnly = true)
    public EncerramentoLoteResultado previewLoteAssessoria() {
        UUID tenantId = TenantContext.getRequiredTenantId();
        LocalDate hoje = hoje();

        List<EncerramentoSemanaResultado> resultados = new ArrayList<>();
        int semPlano = 0;
        for (Atleta atleta : atletaRepository.findAllAtletas(tenantId)) {
            List<PlanoSemanal> planos = planoSemanalRepository.findSemanaCorrente(atleta.getId(), tenantId, hoje);
            if (planos.isEmpty()) {
                semPlano++;
                continue;
            }
            resultados.add(projetarPlano(planos.get(0), hoje));
        }
        return consolidar(resultados, semPlano, List.of());
    }

    @Override
    public int encerrarPlanosElegiveis(UUID tenantId, LocalDate hoje, int carenciaDias) {
        LocalDate limiteCarencia = hoje.minusDays(carenciaDias);
        List<PlanoSemanal> elegiveis = planoSemanalRepository.findElegiveisFallback(tenantId, limiteCarencia);
        if (elegiveis.isEmpty()) {
            return 0;
        }

        // Uma transação por plano: a falha de um não derruba os demais.
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        int encerrados = 0;
        for (PlanoSemanal plano : elegiveis) {
            UUID planoId = plano.getId();
            try {
                tx.execute(status ->
                        encerrarPlanoPorId(planoId, tenantId, hoje, OrigemEncerramento.AUTOMATICO));
                encerrados++;
            } catch (Exception e) {
                log.warn("Falha no fallback ao encerrar plano {}: {}", planoId, e.getMessage());
            }
        }
        return encerrados;
    }

    private EncerramentoLoteResultado consolidar(List<EncerramentoSemanaResultado> resultados,
                                                 int semPlano, List<FalhaAtleta> falhas) {
        int concluidos = (int) resultados.stream()
                .filter(EncerramentoSemanaResultado::prontoParaProximaSemana).count();
        int perdidos = resultados.stream()
                .mapToInt(EncerramentoSemanaResultado::treinosFinalizados).sum();
        return new EncerramentoLoteResultado(resultados.size(), semPlano, concluidos, perdidos, resultados, falhas);
    }

    EncerramentoSemanaResultado encerrarPlanoPorId(UUID planoId, UUID tenantId, LocalDate hoje, OrigemEncerramento origem) {
        PlanoSemanal plano = planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Plano semanal não encontrado"));
        return encerrarPlano(plano, hoje, origem);
    }

    /**
     * Encerra um plano já carregado (e validado quanto ao tenant): finaliza os pendentes elegíveis,
     * fecha o plano quando a semana terminou ({@code hoje >= semanaFim}) — evitando reprocesso
     * perpétuo de plano vazio/sem elegíveis — e publica o {@link SemanaEncerradaEvent}. Reusado pelo
     * fluxo on-demand, pelo lote e pelo fallback automático (que informa {@code origem}).
     *
     * <p><b>Idempotent:</b> YES. <b>Side Effects:</b> Database update + evento. <b>Tenant-aware:</b>
     * YES (o chamador garante o tenant do plano).
     */
    EncerramentoSemanaResultado encerrarPlano(PlanoSemanal plano, LocalDate hoje, OrigemEncerramento origem) {
        boolean jaConcluido = plano.getStatus() == PlanoStatus.CONCLUIDO;
        List<UUID> perdidos = finalizarPendentes(plano, hoje);

        boolean semanaTerminou = !hoje.isBefore(plano.getSemanaFim()); // hoje >= semanaFim
        if (semanaTerminou && plano.getStatus() != PlanoStatus.CONCLUIDO) {
            plano.setStatus(PlanoStatus.CONCLUIDO);
        }

        // Registra a origem apenas quando ESTE encerramento fechou o plano (métrica de adoção).
        boolean fechouAgora = !jaConcluido && plano.getStatus() == PlanoStatus.CONCLUIDO;
        if (fechouAgora) {
            plano.setOrigemEncerramento(origem);
            planoSemanalRepository.save(plano);
        }

        boolean pronto = plano.getStatus() == PlanoStatus.CONCLUIDO;
        String aviso = avisoSemanaNaoTerminou(plano, hoje, pronto);

        eventPublisher.publishEvent(new SemanaEncerradaEvent(
                plano.getId(), plano.getAtleta().getId(), plano.getAssessoria().getId(),
                perdidos.size(), origem));

        log.info("Semana encerrada: plano={}, tenant={}, origem={}, treinosPerdidos={}, status={}",
                plano.getId(), plano.getAssessoria().getId(), origem, perdidos.size(), plano.getStatus());
        return new EncerramentoSemanaResultado(
                plano.getId(), plano.getStatus(), perdidos.size(), perdidos, pronto, origem, aviso);
    }

    /** Projeta o encerramento de um plano SEM persistir (usado no preview do lote). */
    private EncerramentoSemanaResultado projetarPlano(PlanoSemanal plano, LocalDate hoje) {
        List<UUID> elegiveis = elegiveis(plano, hoje).stream().map(TreinoPlanejado::getId).toList();
        boolean fecharia = !hoje.isBefore(plano.getSemanaFim());
        PlanoStatus statusProjetado = fecharia ? PlanoStatus.CONCLUIDO : plano.getStatus();
        String aviso = avisoSemanaNaoTerminou(plano, hoje, fecharia);
        return new EncerramentoSemanaResultado(plano.getId(), statusProjetado, elegiveis.size(),
                elegiveis, fecharia, OrigemEncerramento.ON_DEMAND, aviso);
    }

    private String avisoSemanaNaoTerminou(PlanoSemanal plano, LocalDate hoje, boolean fechado) {
        return (!fechado && hoje.isBefore(plano.getSemanaFim()))
                ? "Semana ainda não terminou; os treinos futuros permanecem pendentes."
                : null;
    }

    /**
     * Marca como PERDIDO os treinos PENDENTE elegíveis do plano, delegando a marcação unitária a
     * {@link TreinoService#marcarTreinoPerdido(UUID)} (que recalcula o status do plano).
     */
    private List<UUID> finalizarPendentes(PlanoSemanal plano, LocalDate hoje) {
        List<UUID> perdidos = new ArrayList<>();
        for (TreinoPlanejado treino : elegiveis(plano, hoje)) {
            treinoService.marcarTreinoPerdido(treino.getId());
            perdidos.add(treino.getId());
        }
        return perdidos;
    }

    /**
     * Treinos elegíveis a PERDIDO: PENDENTE e ({@code dataTreino < hoje}, ou {@code dataTreino == hoje}
     * somente no fim da semana). Um treino que já não está PENDENTE (corrida com registro retroativo)
     * é excluído aqui, sem lançar exceção.
     */
    private List<TreinoPlanejado> elegiveis(PlanoSemanal plano, LocalDate hoje) {
        boolean fimDaSemana = hoje.equals(plano.getSemanaFim());
        return treinoPlanejadoRepository.findPendentesAteHojeDoPlano(plano.getId(), hoje).stream()
                .filter(treino -> treino.getStatusTreino() == TreinoExecucaoStatus.PENDENTE)
                .filter(treino -> treino.getDataTreino().isBefore(hoje)
                        || (treino.getDataTreino().isEqual(hoje) && fimDaSemana))
                .toList();
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(ZONA));
    }
}
