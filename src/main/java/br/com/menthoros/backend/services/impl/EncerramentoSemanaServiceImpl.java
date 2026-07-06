package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.OrigemEncerramento;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.events.SemanaEncerradaEvent;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

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
    public EncerramentoLoteResultado encerrarSemanaLoteAssessoria(List<UUID> atletaIds) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        LocalDate hoje = hoje();
        TransactionTemplate tx = novaTransacaoRequiresNew();

        List<EncerramentoSemanaResultado> resultados = new ArrayList<>();
        List<FalhaAtleta> falhas = new ArrayList<>();
        int semPlano = iterarAtletasComPlanoCorrente(tenantId, hoje, atletaIds, plano -> {
            UUID planoId = plano.getId();
            try {
                resultados.add(tx.execute(status ->
                        encerrarPlanoPorId(planoId, tenantId, hoje, OrigemEncerramento.ON_DEMAND)));
            } catch (Exception e) {
                log.warn("Falha ao encerrar semana do plano {} (atleta {}): {}",
                        planoId, plano.getAtleta().getId(), e.getMessage());
                falhas.add(new FalhaAtleta(plano.getAtleta().getId(), motivoSeguro(e)));
            }
        });
        return consolidar(resultados, semPlano, falhas);
    }

    @Override
    @Transactional(readOnly = true)
    public EncerramentoLoteResultado previewLoteAssessoria(List<UUID> atletaIds) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        LocalDate hoje = hoje();

        List<EncerramentoSemanaResultado> resultados = new ArrayList<>();
        int semPlano = iterarAtletasComPlanoCorrente(tenantId, hoje, atletaIds,
                plano -> resultados.add(projetarPlano(plano, hoje, tenantId)));
        return consolidar(resultados, semPlano, List.of());
    }

    @Override
    public int encerrarPlanosElegiveis(UUID tenantId, LocalDate hoje, int carenciaDias) {
        LocalDate limiteCarencia = hoje.minusDays(carenciaDias);
        List<PlanoSemanal> elegiveis = planoSemanalRepository.findElegiveisFallback(tenantId, limiteCarencia);
        if (elegiveis.isEmpty()) {
            return 0;
        }
        TransactionTemplate tx = novaTransacaoRequiresNew();
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

    // ---- núcleo ----

    private EncerramentoSemanaResultado encerrarPlanoPorId(UUID planoId, UUID tenantId,
                                                           LocalDate hoje, OrigemEncerramento origem) {
        PlanoSemanal plano = planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Plano semanal não encontrado"));
        return encerrarPlano(plano, hoje, origem, tenantId);
    }

    /**
     * Encerra um plano já carregado (e validado quanto ao tenant): finaliza os pendentes elegíveis,
     * fecha o plano quando a semana terminou ({@code hoje >= semanaFim}) — evitando reprocesso
     * perpétuo de plano vazio/sem elegíveis — e publica o {@link SemanaEncerradaEvent} apenas quando
     * houve mudança de estado.
     */
    private EncerramentoSemanaResultado encerrarPlano(PlanoSemanal plano, LocalDate hoje,
                                                      OrigemEncerramento origem, UUID tenantId) {
        boolean jaConcluido = plano.getStatus() == PlanoStatus.CONCLUIDO;
        List<UUID> perdidos = finalizarPendentes(plano, hoje, tenantId);

        if (semanaTerminou(plano, hoje) && plano.getStatus() != PlanoStatus.CONCLUIDO) {
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

        // Publica somente quando houve mudança real (evita evento em replay idempotente).
        if (fechouAgora || !perdidos.isEmpty()) {
            eventPublisher.publishEvent(new SemanaEncerradaEvent(
                    plano.getId(), plano.getAtleta().getId(), plano.getAssessoria().getId(),
                    perdidos.size(), origem));
        }

        log.info("Semana encerrada: plano={}, tenant={}, origem={}, treinosPerdidos={}, status={}",
                plano.getId(), plano.getAssessoria().getId(), origem, perdidos.size(), plano.getStatus());
        return new EncerramentoSemanaResultado(
                plano.getId(), plano.getStatus(), perdidos.size(), perdidos, pronto, origem, aviso);
    }

    /** Projeta o encerramento de um plano SEM persistir (usado no preview do lote). */
    private EncerramentoSemanaResultado projetarPlano(PlanoSemanal plano, LocalDate hoje, UUID tenantId) {
        List<UUID> elegiveis = elegiveis(plano, hoje, tenantId).stream().map(TreinoPlanejado::getId).toList();
        boolean fecharia = semanaTerminou(plano, hoje);
        PlanoStatus statusProjetado = fecharia ? PlanoStatus.CONCLUIDO : plano.getStatus();
        String aviso = avisoSemanaNaoTerminou(plano, hoje, fecharia);
        return new EncerramentoSemanaResultado(plano.getId(), statusProjetado, elegiveis.size(),
                elegiveis, fecharia, OrigemEncerramento.ON_DEMAND, aviso);
    }

    /** Marca em lote como PERDIDO os treinos elegíveis do plano (1 recálculo ao final — sem N+1). */
    private List<UUID> finalizarPendentes(PlanoSemanal plano, LocalDate hoje, UUID tenantId) {
        List<TreinoPlanejado> elegiveis = elegiveis(plano, hoje, tenantId);
        treinoService.marcarTreinosPerdidos(plano, elegiveis);
        return elegiveis.stream().map(TreinoPlanejado::getId).toList();
    }

    /**
     * Treinos elegíveis a PERDIDO: PENDENTE e ({@code dataTreino < hoje}, ou {@code dataTreino == hoje}
     * somente no fim da semana).
     */
    private List<TreinoPlanejado> elegiveis(PlanoSemanal plano, LocalDate hoje, UUID tenantId) {
        boolean fimDaSemana = hoje.equals(plano.getSemanaFim());
        return treinoPlanejadoRepository.findPendentesAteHojeDoPlano(plano.getId(), tenantId, hoje).stream()
                // defesa contra corrida: o treino pode ter virado REALIZADO entre a query e este ponto
                // (registro retroativo de atividade) — nesse caso é ignorado, sem lançar.
                .filter(treino -> treino.getStatusTreino() == TreinoExecucaoStatus.PENDENTE)
                .filter(treino -> treino.getDataTreino().isBefore(hoje)
                        || (treino.getDataTreino().isEqual(hoje) && fimDaSemana))
                .toList();
    }

    // ---- helpers de lote ----

    /**
     * Itera os atletas do tenant, resolve a semana corrente de cada um e aplica o {@code processador}
     * ao plano encontrado. Retorna quantos atletas ficaram sem plano na semana corrente.
     */
    private int iterarAtletasComPlanoCorrente(UUID tenantId, LocalDate hoje, List<UUID> atletaIds,
                                              Consumer<PlanoSemanal> processador) {
        // Filtro opcional por atletas selecionados. Interseção com os atletas do tenant (findAllAtletas
        // é tenant-scoped): um id de outro tenant simplesmente não aparece → sem vazamento cross-tenant.
        Set<UUID> filtro = (atletaIds == null || atletaIds.isEmpty()) ? null : new HashSet<>(atletaIds);

        // 1 query para todos os planos correntes do tenant (evita N+1). Agrupa por atleta escolhendo
        // o mais recente (query ordenada por semanaInicio desc → putIfAbsent guarda o primeiro/mais recente).
        Map<UUID, PlanoSemanal> planoPorAtleta = new LinkedHashMap<>();
        for (PlanoSemanal plano : planoSemanalRepository.findSemanasCorrentes(tenantId, hoje)) {
            UUID atletaId = plano.getAtleta().getId();
            if (planoPorAtleta.putIfAbsent(atletaId, plano) != null) {
                log.warn("Sobreposição de semanas na semana corrente do atleta {}; usando o mais recente", atletaId);
            }
        }

        int semPlano = 0;
        for (Atleta atleta : atletaRepository.findAllAtletas(tenantId)) {
            if (filtro != null && !filtro.contains(atleta.getId())) {
                continue;
            }
            PlanoSemanal plano = planoPorAtleta.get(atleta.getId());
            if (plano == null) {
                semPlano++;
                continue;
            }
            processador.accept(plano);
        }
        return semPlano;
    }

    private EncerramentoLoteResultado consolidar(List<EncerramentoSemanaResultado> resultados,
                                                 int semPlano, List<FalhaAtleta> falhas) {
        int concluidos = (int) resultados.stream()
                .filter(EncerramentoSemanaResultado::prontoParaProximaSemana).count();
        int perdidos = resultados.stream()
                .mapToInt(EncerramentoSemanaResultado::treinosFinalizados).sum();
        return new EncerramentoLoteResultado(resultados.size(), semPlano, concluidos, perdidos, resultados, falhas);
    }

    private TransactionTemplate novaTransacaoRequiresNew() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }

    /** Mensagem segura para a resposta HTTP: só propaga erros de domínio; o resto vira genérico. */
    private String motivoSeguro(Exception e) {
        if (e instanceof DomainNotFoundException || e instanceof DomainRuleViolationException) {
            return e.getMessage();
        }
        return "Encerramento não concluído; verifique o log.";
    }

    private boolean semanaTerminou(PlanoSemanal plano, LocalDate hoje) {
        return !hoje.isBefore(plano.getSemanaFim()); // hoje >= semanaFim
    }

    private String avisoSemanaNaoTerminou(PlanoSemanal plano, LocalDate hoje, boolean fechado) {
        return (!fechado && hoje.isBefore(plano.getSemanaFim()))
                ? "Semana ainda não terminou; os treinos futuros permanecem pendentes."
                : null;
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(ZONA));
    }
}
