package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaFC;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contexto de uma execução da receita de normalização: só dados do atleta e do plano, pré-computados
 * uma vez por plano semanal por {@code PlanoLlmValidator}. Colaboradores não entram aqui — são
 * campos de {@link NormalizacaoDeTreino} (decisão Q15 do design).
 *
 * <p>É onde {@code PerfilFisiologico} (#3 da revisão de arquitetura) e {@code PerfilAtletaPlano}
 * (#4) pousam depois: hoje 5 campos, o alvo é 1.</p>
 *
 * @param zonasFC {@code null} quando o atleta não tem FC cadastrada — {@code corrigir-fc-zona} vira no-op
 */
public record ContextoNormalizacao(
        Atleta atleta,
        UUID atletaId,
        @Nullable List<ZonaFC> zonasFC,
        Map<TipoTreino, BigDecimal> tetoPorTipo,
        Map<TipoTreino, BigDecimal> pisoPorTipo
) {}
