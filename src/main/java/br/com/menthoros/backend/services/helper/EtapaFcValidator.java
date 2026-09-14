package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaFC;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Valida e corrige a FC alvo de uma etapa contra a zona fisiológica esperada (LTHR),
 * considerando o tipo de etapa e o tipo de treino.
 * Extraído de {@code IaServiceImpl} (refactor-iaservice-decomposition, seção 4).
 */
@Slf4j
@Component
public class EtapaFcValidator {

    /**
     * Extrai o range de FC do formato "NNN-NNN bpm".
     * Retorna null se o formato não for reconhecido ou o valor for nulo.
     */
    int[] parseFcRange(String fcAlvoEtapa) {
        if (fcAlvoEtapa == null) return null;
        var matcher = Pattern.compile("^(\\d{2,3})-(\\d{2,3}) bpm$").matcher(fcAlvoEtapa.trim());
        if (!matcher.matches()) return null;
        return new int[]{ Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)) };
    }

    /**
     * Retorna o range de FC esperado para o tipo de etapa, considerando também o tipo de treino.
     * <p>O tipoTreino afina o mapeamento da etapa PRINCIPAL, que varia de Z1-Z2 (REGENERATIVO)
     * até Z4-Z5 (INTERVALADO/TIRO). Sem tipoTreino, PRINCIPAL cai no default Z2-Z4.</p>
     * <p><b>IA-02 (review.md 2026-09-05):</b> etapas {@code INTERVALADO} e {@code RECUPERACAO}
     * também variam por {@code tipoTreino} — a expansão de fartlek (`TreinoNormalizador`) já
     * computa a FC correta pra aceleração (Z2-Z4) e recuperação (Z1-Z2) via {@code zonaParaFc},
     * mas antes deste fix a validação sobrescrevia pra Z4-Z5/Z1 fixo, ignorando o tipo de treino.
     * `AQUECIMENTO`/`DESAQUECIMENTO` continuam Z1 fixo em qualquer treino (não fazem parte do
     * estímulo, sempre leves).</p>
     * <table border="1">
     *   <tr><th>tipoEtapa</th><th>tipoTreino</th><th>Zona</th></tr>
     *   <tr><td>AQUECIMENTO / DESAQUECIMENTO</td><td>qualquer</td><td>Z1</td></tr>
     *   <tr><td>RECUPERACAO</td><td>FARTLEK</td><td>Z1–Z2</td></tr>
     *   <tr><td>RECUPERACAO</td><td>demais/default</td><td>Z1</td></tr>
     *   <tr><td>INTERVALADO</td><td>FARTLEK</td><td>Z2–Z4</td></tr>
     *   <tr><td>INTERVALADO</td><td>demais/default</td><td>Z4–Z5</td></tr>
     *   <tr><td>PRINCIPAL</td><td>REGENERATIVO</td><td>Z1–Z2</td></tr>
     *   <tr><td>PRINCIPAL</td><td>CONTINUO / FACIL / LONGO</td><td>Z2–Z3</td></tr>
     *   <tr><td>PRINCIPAL</td><td>FARTLEK</td><td>Z2–Z4</td></tr>
     *   <tr><td>PRINCIPAL</td><td>TEMPO_RUN</td><td>Z3–Z4</td></tr>
     *   <tr><td>PRINCIPAL</td><td>INTERVALADO / TIRO</td><td>Z4–Z5</td></tr>
     *   <tr><td>PRINCIPAL</td><td>default/null</td><td>Z2–Z4</td></tr>
     * </table>
     */
    int[] zonaEsperadaFC(String tipoEtapa, String tipoTreino, List<ZonaFC> zonasFC) {
        if (tipoEtapa == null || zonasFC == null || zonasFC.size() < 5) return null;
        return switch (tipoEtapa.toUpperCase()) {
            case "AQUECIMENTO", "DESAQUECIMENTO" ->
                    new int[]{ zonasFC.get(0).fcMin(), zonasFC.get(0).fcMax() }; // Z1
            case "RECUPERACAO" -> "FARTLEK".equalsIgnoreCase(tipoTreino)
                    ? new int[]{ zonasFC.get(0).fcMin(), zonasFC.get(1).fcMax() } // Z1-Z2
                    : new int[]{ zonasFC.get(0).fcMin(), zonasFC.get(0).fcMax() }; // Z1
            case "PRINCIPAL" -> zonaParaEtapaPrincipal(tipoTreino, zonasFC);
            case "INTERVALADO" -> zonaParaEtapaPrincipal(tipoTreino, zonasFC);
            default -> null;
        };
    }

    /** Resolve a zona esperada para etapa PRINCIPAL com base no tipo do treino. */
    int[] zonaParaEtapaPrincipal(String tipoTreino, List<ZonaFC> zonasFC) {
        if (tipoTreino == null) return new int[]{ zonasFC.get(1).fcMin(), zonasFC.get(3).fcMax() }; // Z2-Z4 default
        return switch (tipoTreino.toUpperCase()) {
            case "REGENERATIVO"            -> new int[]{ zonasFC.get(0).fcMin(), zonasFC.get(1).fcMax() }; // Z1-Z2
            case "CONTINUO", "FACIL", "LONGO" -> new int[]{ zonasFC.get(1).fcMin(), zonasFC.get(2).fcMax() }; // Z2-Z3
            case "FARTLEK"                 -> new int[]{ zonasFC.get(1).fcMin(), zonasFC.get(3).fcMax() }; // Z2-Z4
            case "TEMPO_RUN"               -> new int[]{ zonasFC.get(2).fcMin(), zonasFC.get(3).fcMax() }; // Z3-Z4
            case "INTERVALADO", "TIRO"     -> new int[]{ zonasFC.get(3).fcMin(), zonasFC.get(4).fcMax() }; // Z4-Z5
            default                        -> new int[]{ zonasFC.get(1).fcMin(), zonasFC.get(3).fcMax() }; // Z2-Z4
        };
    }

    /**
     * Verifica se o {@code fcAlvoEtapa} tem sobreposição ≥50% com a zona fisiológica esperada.
     * <p>Em caso de divergência, corrige o valor para o quartil central da zona esperada
     * e registra um {@code WARN}. Nunca lança exceção — manter o plano válido é prioridade.</p>
     */
    public EtapaTreinoLlmDto validarFcEtapa(EtapaTreinoLlmDto etapa, String tipoTreino, List<ZonaFC> zonasFC) {
        int[] prescrito = parseFcRange(etapa.fcAlvoEtapa());
        if (prescrito == null) {
            if (etapa.fcAlvoEtapa() != null) {
                log.warn("fcAlvoEtapa não parseable, mantendo original: tipo='{}' valor='{}'",
                        etapa.tipoEtapa(), etapa.fcAlvoEtapa());
            }
            return etapa;
        }

        int[] esperado = zonaEsperadaFC(etapa.tipoEtapa(), tipoTreino, zonasFC);
        if (esperado == null) return etapa;

        int prescMin = prescrito[0], prescMax = prescrito[1];
        int espMin   = esperado[0],  espMax   = esperado[1];

        int overlap = Math.max(0, Math.min(prescMax, espMax) - Math.max(prescMin, espMin));
        int larguraPrescrita = Math.max(1, prescMax - prescMin);
        double overlapPct = (double) overlap / larguraPrescrita;

        if (overlapPct < 0.50) {
            // Corrigir para o quartil central da zona esperada
            int amplitude = espMax - espMin;
            int centroMin = espMin + amplitude / 4;
            int centroMax = espMax - amplitude / 4;
            String fcCorrigida = centroMin + "-" + centroMax + " bpm";
            log.warn("FC fora da zona esperada: tipo='{}', prescrito='{}', esperado='{}-{} bpm', corrigindo para '{}'",
                    etapa.tipoEtapa(), etapa.fcAlvoEtapa(), espMin, espMax, fcCorrigida);
            return new EtapaTreinoLlmDto(
                    etapa.ordem(), etapa.tipoEtapa(), etapa.descricaoEtapa(),
                    etapa.duracaoMin(), etapa.distanciaKm(), fcCorrigida, etapa.repeticoes(), etapa.ritmoAlvo()
            );
        }
        return etapa;
    }
}
