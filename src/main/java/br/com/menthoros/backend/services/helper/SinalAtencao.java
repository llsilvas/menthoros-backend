package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.output.CoachAttentionItemOutputDto.Evidencia;
import br.com.menthoros.backend.enums.MotivoAtencao;
import br.com.menthoros.backend.enums.Severidade;

import java.util.List;

/**
 * Sinal de atenção bruto, derivado de uma fonte (fadiga, sobrecarga, ...) para um atleta, antes da
 * consolidação por atleta na fila. Interno à camada de serviço — não é contrato de API.
 */
public record SinalAtencao(
        MotivoAtencao motivo,
        Severidade severidade,
        List<Evidencia> evidencias
) {}
