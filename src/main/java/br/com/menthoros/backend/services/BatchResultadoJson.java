package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.BatchJobStatusOutputDto.BatchErroItemDto;
import br.com.menthoros.backend.dto.output.BatchJobStatusOutputDto.BatchGeradoItemDto;

import java.util.List;

/**
 * Forma do JSON persistido em {@code tb_batch_plan_job.resultado} no estado
 * terminal — serializado pelo processador e lido de volta pelo GET de status.
 *
 * <p>{@code observacao} é uma nota de nível de job (nula no fluxo normal);
 * usada pelo recovery de jobs órfãos para registrar a interrupção por reinício.
 */
public record BatchResultadoJson(
        List<BatchGeradoItemDto> gerados,
        List<BatchErroItemDto> erros,
        String observacao
) {}
