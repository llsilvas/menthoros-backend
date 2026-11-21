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
 * @param zonasPace {@code null} sem pace de limiar nem zonas manuais de pace — {@code corrigir-pace-zona} vira no-op
 */
public record ContextoNormalizacao(
        Atleta atleta,
        UUID atletaId,
        @Nullable List<ZonaFC> zonasFC,
        Map<TipoTreino, BigDecimal> tetoPorTipo,
        Map<TipoTreino, BigDecimal> pisoPorTipo,
        @Nullable List<ZonaTreinoService.ZonaPace> zonasPace
) {
    /** Sem zonas de pace — eval e testes anteriores a validar-ritmo-etapa-contra-zona. */
    public ContextoNormalizacao(Atleta atleta, UUID atletaId, @Nullable List<ZonaFC> zonasFC,
                                Map<TipoTreino, BigDecimal> tetoPorTipo, Map<TipoTreino, BigDecimal> pisoPorTipo) {
        this(atleta, atletaId, zonasFC, tetoPorTipo, pisoPorTipo, null);
    }

    /** Zonas de pace só com dado (limiar ou faixas manuais); sem ele, o fallback sintético não vale como régua. */
    public static @Nullable List<ZonaTreinoService.ZonaPace> zonasPaceComDado(Atleta atleta, ZonaTreinoService zonas) {
        boolean manual = atleta.getModoZonasPace() == br.com.menthoros.backend.enums.ModoZonas.MANUAL
                && atleta.getZonasManuaisPace() != null;
        boolean limiar = atleta.getPaceLimiar() != null && atleta.getPaceLimiar().signum() > 0;
        return manual || limiar ? zonas.zonasPace(atleta) : null;
    }
}
