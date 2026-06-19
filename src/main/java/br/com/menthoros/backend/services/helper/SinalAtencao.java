package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.output.CoachAttentionItemOutputDto.Evidencia;
import br.com.menthoros.backend.enums.MotivoAtencao;
import br.com.menthoros.backend.enums.Severidade;

import java.util.List;

/**
 * Sinal de atenção bruto, derivado de uma fonte (fadiga, sobrecarga, ...) para um atleta, antes da
 * consolidação por atleta na fila. Interno à camada de serviço — não é contrato de API.
 *
 * <p>{@code rationale} é uma sentença PT-BR concreta explicando por que o sinal foi acionado
 * (ex.: "TSB em -40.0 situa-se na zona CRITICO, indicando fadiga excessiva.").
 * {@code sourceRules} lista os classificadores/regras que dispararam o sinal no formato
 * {@code "ClassName.methodOrConstant"} — informativo, não executável.
 */
public record SinalAtencao(
        MotivoAtencao motivo,
        Severidade severidade,
        List<Evidencia> evidencias,
        String rationale,
        List<String> sourceRules
) {}
