package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.BatchJobStatusOutputDto.BatchErroItemDto;
import br.com.menthoros.backend.dto.output.BatchJobStatusOutputDto.BatchGeradoItemDto;
import br.com.menthoros.backend.entity.BatchPlanJob;
import br.com.menthoros.backend.enums.BatchJobStatus;
import br.com.menthoros.backend.repository.BatchPlanJobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Fecha jobs de geração em lote que ficaram órfãos (presos em PENDENTE/EM_PROGRESSO)
 * após uma queda da aplicação — o processamento é fire-and-forget em virtual threads.
 *
 * <p>Os planos já gerados persistem (transação curta por atleta); o recovery apenas
 * fecha o job em nível de contagem — o schema não guarda a lista de atletaIds nem o
 * detalhe parcial, então não é possível (nem necessário) nomear os faltantes. O coach
 * reenvia o lote (atletas já com plano caem no índice único).
 */
@Slf4j
@Component
public class BatchPlanRecoveryService {

    private final BatchPlanJobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final long limiteMinutos;

    public BatchPlanRecoveryService(BatchPlanJobRepository jobRepository,
                                    ObjectMapper objectMapper,
                                    @Value("${app.batch-plan.recovery-limite-min:30}") long limiteMinutos) {
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
        this.limiteMinutos = limiteMinutos;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        recuperarJobsOrfaos();
    }

    /**
     * Fecha os jobs órfãos como {@code CONCLUIDO_COM_ERROS}.
     *
     * Idempotent: YES — só atua sobre jobs não-terminais; rodar de novo não altera
     *   um job já fechado (não é mais retornado por {@code findJobsOrfaos}).
     * Side Effects: UPDATE dos jobs órfãos.
     * Tenant-aware: NO — tarefa de manutenção do sistema, sobre todos os tenants.
     */
    @Transactional
    public void recuperarJobsOrfaos() {
        Instant limite = Instant.now().minus(Duration.ofMinutes(limiteMinutos));
        List<BatchPlanJob> orfaos = jobRepository.findJobsOrfaos(limite);
        if (orfaos.isEmpty()) {
            return;
        }
        log.warn("[batch-plan] recovery: fechando {} job(s) órfão(s) presos em andamento", orfaos.size());
        orfaos.forEach(this::fecharComoInterrompido);
    }

    private void fecharComoInterrompido(BatchPlanJob job) {
        int naoProcessados = job.getTotalAtletas() - job.getGerados() - job.getErros();
        String observacao = "Lote interrompido por reinício da aplicação. " + naoProcessados
                + " atleta(s) podem não ter sido processados — reenvie o lote.";
        job.setStatus(BatchJobStatus.CONCLUIDO_COM_ERROS);
        job.setConcluidoEm(Instant.now());
        job.setResultado(serializar(observacao));
        jobRepository.save(job);
    }

    private String serializar(String observacao) {
        try {
            return objectMapper.writeValueAsString(
                    new BatchResultadoJson(List.<BatchGeradoItemDto>of(), List.<BatchErroItemDto>of(), observacao));
        } catch (JsonProcessingException e) {
            log.error("[batch-plan] recovery: falha ao serializar a observação: {}", e.getMessage());
            return null;
        }
    }
}
