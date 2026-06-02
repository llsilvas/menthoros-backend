package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.TipoTreino;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Validator estrutural de consistência entre o tipo de treino declarado pelo coach
 * e a distribuição real de esforço observada nas etapas (via IF por etapa).
 *
 * <p>IF (Intensity Factor) = fcMedia_etapa / fcLimiar_atleta.
 *
 * <h3>Regras implementadas:</h3>
 * <ul>
 *   <li><strong>REGENERATIVO:</strong> IF médio ≤ 0.70 e nenhuma etapa com IF &gt; 0.80</li>
 *   <li><strong>CONTINUO:</strong> IF médio em [0.70, 0.90] e variação intra-treino &lt; 20%</li>
 *   <li><strong>TEMPO_RUN:</strong> IF médio em [0.85, 0.95] e pelo menos uma etapa com IF &gt; 0.85
 *       (segmento central dominante)</li>
 * </ul>
 *
 * <p>Se o {@code fcLimiar} do atleta não estiver disponível, ou se não houver etapas,
 * o método retorna {@code Optional.empty()} sem sugestão — nunca lança exceção neste caso.
 *
 * <p>A sugestão é informativa: o tipo declarado pelo coach NUNCA é alterado automaticamente.
 */
@Slf4j
@Component
public class TipoTreinoConsistenciaValidator {

    // ===== Limites para REGENERATIVO =====
    private static final double REGEN_IF_MEDIO_MAX = 0.70;
    private static final double REGEN_IF_PICO_MAX  = 0.80;

    // ===== Limites para CONTINUO =====
    private static final double CONTINUO_IF_MIN              = 0.70;
    private static final double CONTINUO_IF_MAX              = 0.90;
    private static final double CONTINUO_VARIACAO_MAX        = 0.20; // max - min

    // ===== Limites para TEMPO_RUN =====
    private static final double TEMPO_IF_MIN               = 0.85;
    private static final double TEMPO_IF_MAX               = 0.95;
    private static final double TEMPO_SEGMENTO_DOMINANTE   = 0.85;

    // ===== Confiança =====
    private static final double CONFIANCA_ALTA   = 0.90;
    private static final double CONFIANCA_MEDIA  = 0.75;

    /**
     * Analisa a consistência estrutural do treino com o tipo declarado.
     *
     * <p><strong>Idempotent:</strong> YES — leitura pura, nenhum estado é alterado.
     * <p><strong>Side Effects:</strong> NONE
     * <p><strong>Tenant-aware:</strong> NO — operação puramente em memória.
     *
     * @param treino o treino realizado com suas etapas e atleta associado
     * @return {@code Optional} com sugestão de reclassificação se houver divergência,
     *         ou {@code Optional.empty()} se coerente ou se não houver dados suficientes
     * @throws IllegalArgumentException se {@code treino} for null
     */
    public Optional<SugestaoReclassificacao> validarEstrutura(TreinoRealizado treino) {
        if (treino == null) {
            throw new IllegalArgumentException("TreinoRealizado não pode ser null");
        }

        // Sem etapas — não há estrutura para validar
        List<EtapaRealizada> etapas = treino.getEtapasRealizadas();
        if (etapas == null || etapas.isEmpty()) {
            log.debug("Treino {} sem etapas — validação estrutural ignorada", treino.getId());
            return Optional.empty();
        }

        // Sem fcLimiar — IF não pode ser calculado
        Integer fcLimiar = resolverFcLimiar(treino);
        if (fcLimiar == null || fcLimiar <= 0) {
            log.debug("Treino {} — atleta sem fcLimiar, validação estrutural ignorada", treino.getId());
            return Optional.empty();
        }

        // Calcular IFs por etapa (apenas etapas com fcMedia válida)
        List<Double> ifs = etapas.stream()
                .filter(e -> e.getFcMedia() != null && e.getFcMedia() > 0)
                .map(e -> (double) e.getFcMedia() / fcLimiar)
                .toList();

        if (ifs.isEmpty()) {
            log.debug("Treino {} — nenhuma etapa com fcMedia válida, validação estrutural ignorada", treino.getId());
            return Optional.empty();
        }

        TipoTreino tipoDeclarado = treino.getTipoTreino();
        if (tipoDeclarado == null) {
            return Optional.empty();
        }

        return switch (tipoDeclarado) {
            case REGENERATIVO -> validarRegenerativo(ifs);
            case CONTINUO     -> validarContinuo(ifs);
            case TEMPO_RUN    -> validarTempoRun(ifs);
            default           -> Optional.empty(); // demais tipos sem regras estruturais definidas
        };
    }

    // =========================================================================
    // Validações por tipo
    // =========================================================================

    private Optional<SugestaoReclassificacao> validarRegenerativo(List<Double> ifs) {
        double ifMedio = calcularMedia(ifs);
        double ifPico  = ifs.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        // Verificar pico antes do IF médio — pico é mais restritivo para REGENERATIVO
        if (ifPico > REGEN_IF_PICO_MAX) {
            String motivo = String.format(
                    "Etapa com IF=%.2f detectada (limite REGENERATIVO: IF_pico ≤ %.2f). "
                            + "IF médio=%.2f. Estrutura sugere %s.",
                    ifPico, REGEN_IF_PICO_MAX, ifMedio, TipoTreino.CONTINUO.getLabel());
            log.debug("REGENERATIVO inconsistente por pico: {}", motivo);
            return Optional.of(new SugestaoReclassificacao(TipoTreino.CONTINUO, CONFIANCA_ALTA, motivo));
        }

        if (ifMedio > REGEN_IF_MEDIO_MAX) {
            String motivo = String.format(
                    "IF médio=%.2f excede limite de REGENERATIVO (%.2f). "
                            + "Estrutura sugere %s.",
                    ifMedio, REGEN_IF_MEDIO_MAX, TipoTreino.CONTINUO.getLabel());
            log.debug("REGENERATIVO inconsistente por IF médio: {}", motivo);
            return Optional.of(new SugestaoReclassificacao(TipoTreino.CONTINUO, CONFIANCA_MEDIA, motivo));
        }

        return Optional.empty();
    }

    private Optional<SugestaoReclassificacao> validarContinuo(List<Double> ifs) {
        double ifMin      = ifs.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double ifMax      = ifs.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double variacao   = ifMax - ifMin;
        double ifMedio    = calcularMedia(ifs);

        if (variacao > CONTINUO_VARIACAO_MAX) {
            String motivo = String.format(
                    "Variação intra-treino de IF=%.2f excede limite de CONTINUO (%.2f). "
                            + "IF min=%.2f, max=%.2f, médio=%.2f. Treino apresenta padrão de intensidade variável.",
                    variacao, CONTINUO_VARIACAO_MAX, ifMin, ifMax, ifMedio);
            log.debug("CONTINUO inconsistente por variação: {}", motivo);
            // Sugere FARTLEK se a variação é grande mas não há padrão de intervalado claro
            TipoTreino sugestao = ifMedio >= TEMPO_IF_MIN ? TipoTreino.TEMPO_RUN : TipoTreino.FARTLEK;
            return Optional.of(new SugestaoReclassificacao(sugestao, CONFIANCA_MEDIA, motivo));
        }

        return Optional.empty();
    }

    private Optional<SugestaoReclassificacao> validarTempoRun(List<Double> ifs) {
        boolean temSegmentoDominante = ifs.stream().anyMatch(ifVal -> ifVal > TEMPO_SEGMENTO_DOMINANTE);

        if (!temSegmentoDominante) {
            double ifMedio = calcularMedia(ifs);
            String motivo = String.format(
                    "Nenhuma etapa com IF > %.2f encontrada (critério TEMPO_RUN). "
                            + "IF médio=%.2f. Estrutura sugere %s.",
                    TEMPO_SEGMENTO_DOMINANTE, ifMedio, TipoTreino.CONTINUO.getLabel());
            log.debug("TEMPO_RUN inconsistente — sem segmento dominante: {}", motivo);
            return Optional.of(new SugestaoReclassificacao(TipoTreino.CONTINUO, CONFIANCA_ALTA, motivo));
        }

        return Optional.empty();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Integer resolverFcLimiar(TreinoRealizado treino) {
        if (treino.getAtleta() == null) return null;
        return treino.getAtleta().getFcLimiar();
    }

    private double calcularMedia(List<Double> valores) {
        return valores.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }
}
