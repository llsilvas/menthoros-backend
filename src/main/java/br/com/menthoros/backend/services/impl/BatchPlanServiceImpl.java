package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.BatchGeracaoPlanoInputDto;
import br.com.menthoros.backend.dto.output.BatchJobStatusOutputDto;
import br.com.menthoros.backend.dto.output.BatchJobStatusOutputDto.BatchErroItemDto;
import br.com.menthoros.backend.dto.output.BatchJobStatusOutputDto.BatchGeradoItemDto;
import br.com.menthoros.backend.dto.output.BatchLoteAceitoOutputDto;
import br.com.menthoros.backend.entity.BatchPlanJob;
import br.com.menthoros.backend.enums.BatchJobStatus;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.repository.BatchPlanJobRepository;
import br.com.menthoros.backend.services.BatchPlanProcessor;
import br.com.menthoros.backend.services.BatchPlanService;
import br.com.menthoros.backend.services.BatchResultadoJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class BatchPlanServiceImpl implements BatchPlanService {

    private final BatchPlanJobRepository jobRepository;
    private final BatchPlanProcessor batchPlanProcessor;
    private final ObjectMapper objectMapper;

    public BatchPlanServiceImpl(BatchPlanJobRepository jobRepository,
                                BatchPlanProcessor batchPlanProcessor,
                                ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.batchPlanProcessor = batchPlanProcessor;
        this.objectMapper = objectMapper;
    }

    /**
     * Idempotent: NO — cria um novo job a cada chamada.
     * Side Effects: INSERT do job + disparo assíncrono da geração.
     * Tenant-aware: YES — tenantId capturado na thread HTTP e propagado ao fluxo assíncrono.
     */
    @Override
    @Transactional
    public BatchLoteAceitoOutputDto iniciarLote(BatchGeracaoPlanoInputDto input, UUID tenantId) {
        List<UUID> atletaIds = input.atletaIds().stream().distinct().toList();

        BatchPlanJob job = new BatchPlanJob();
        job.setTenantId(tenantId);
        job.setStatus(BatchJobStatus.PENDENTE);
        job.setTotalAtletas(atletaIds.size());
        job = jobRepository.save(job);

        log.info("[batch-plan] job {} criado com {} atleta(s) (tenant {})", job.getId(), atletaIds.size(), tenantId);

        batchPlanProcessor.processarLote(job.getId(), atletaIds, input.modo(), tenantId);
        return new BatchLoteAceitoOutputDto(job.getId(), atletaIds.size());
    }

    /**
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE.
     * Tenant-aware: YES — 404 se o job não pertence ao tenant.
     */
    @Override
    @Transactional(readOnly = true)
    public BatchJobStatusOutputDto consultarStatus(UUID jobId, UUID tenantId) {
        BatchPlanJob job = jobRepository.findByIdAndTenantId(jobId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Job de geração em lote não encontrado"));

        BatchResultadoJson detalhes = desserializarResultado(job);
        return new BatchJobStatusOutputDto(
                job.getId(),
                job.getStatus(),
                job.getTotalAtletas(),
                job.getGerados(),
                job.getErros(),
                detalhes.gerados(),
                detalhes.erros()
        );
    }

    /**
     * Detalhes só existem no estado terminal; durante o progresso as listas vêm vazias.
     */
    private BatchResultadoJson desserializarResultado(BatchPlanJob job) {
        if (job.getResultado() == null || job.getResultado().isBlank()) {
            return new BatchResultadoJson(List.<BatchGeradoItemDto>of(), List.<BatchErroItemDto>of());
        }
        try {
            return objectMapper.readValue(job.getResultado(), BatchResultadoJson.class);
        } catch (Exception e) {
            log.error("[batch-plan] falha ao ler o resultado do job {}: {}", job.getId(), e.getMessage());
            return new BatchResultadoJson(List.<BatchGeradoItemDto>of(), List.<BatchErroItemDto>of());
        }
    }
}
