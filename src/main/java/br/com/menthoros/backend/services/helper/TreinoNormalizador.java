package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaFC;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Normalização determinística de treinos INTERVALADO/TIRO/FARTLEK e de treinos contínuos sem
 * distância — expansão de séries comprimidas pelo LLM ("6x400m", "4x (1min Z2 + 2min Z1)"),
 * ajuste de distância por etapa e correção de distância a partir de duração×pace.
 *
 * <p>Extraído de {@code IaServiceImpl} (refactor-iaservice-decomposition, seção 3). Pura
 * transformação de DTOs — sem I/O, sem estado.</p>
 *
 * <p>Idempotent: YES — mesma entrada sempre produz a mesma saída. Side Effects: NONE.
 * Tenant-aware: NO.</p>
 */
@Slf4j
@Component
public class TreinoNormalizador {

    private final PaceValidator paceValidator;

    public TreinoNormalizador(PaceValidator paceValidator) {
        this.paceValidator = paceValidator;
    }

    private static final double PACE_Z2_DEFAULT_MIN_KM = 7.0;  // 7:00/km — Z2 genérico sem limiar cadastrado
    private static final double PACE_Z1_DEFAULT_MIN_KM = 8.0;  // 8:00/km — Z1 genérico sem limiar cadastrado
    private static final double FATOR_PACE_Z2 = 1.20;          // Z2 ≈ limiar × 1.20
    private static final double FATOR_PACE_Z1 = 1.35;          // Z1 ≈ limiar × 1.35

    // Detecta "NxDist" como "6x400m", "8 x 200", "5×1000m"
    // O lookahead (?!\s*min) impede que "5x 2min" seja lido como 5 tiros de 2 metros: sem ele o
    // grupo (m|km)? casa o "m" de "min" e o treino por tempo cai no caminho de distância.
    // IA-03 (review.md 2026-09-05): o grupo do número era (\d+) simples, que faz backtrack pro
    // dígito errado pra satisfazer o (?!\s*min) — em "5 x 10 min" falhava com "10" mas passava
    // com "1", e em "4x1.5km" o "." interrompia (\d+) e o (?!\s*min) trivialmente satisfeito
    // aceitava "1" sozinho. Grupo atômico (?>...) impede esse backtrack: se o número completo
    // (com decimal opcional) não satisfizer o lookahead, a tentativa nessa posição falha inteira,
    // em vez de recuar pra um prefixo mais curto.
    private static final Pattern REPETICOES_PATTERN =
            Pattern.compile("(\\d{1,2})\\s*[xX×]\\s*(?>(\\d+(?:\\.\\d+)?))(?!\\s*min)\\s*(m|km)?",
                    Pattern.CASE_INSENSITIVE);

    // Detecta "Nx (AccelMin + RecovMin)" como "4x (1min Z2 + 2min Z1)", "6 x (2min Z4 + 1min Z2)"
    private static final Pattern FARTLEK_TEMPO_PATTERN =
            Pattern.compile("(\\d{1,2})\\s*[xX×]\\s*\\(?\\s*(\\d+)\\s*min[^+]*\\+\\s*(\\d+)\\s*min",
                    Pattern.CASE_INSENSITIVE);

    /** Tipos de etapa que podem carregar uma série comprimida na descrição. */
    private static final Set<String> ETAPAS_EXPANSIVEIS = Set.of("INTERVALADO", "PRINCIPAL");

    /** Tipos contínuos cuja etapa PRINCIPAL é tempo-baseada (distância = duração/pace). */
    private static final Set<String> TIPOS_CONTINUOS =
            Set.of("REGENERATIVO", "FACIL", "CONTINUO", "LONGO", "TEMPO_RUN");

    private record FartlekParams(int n, int duracaoAceleracao, int duracaoRecuperacao,
                                  String zonaAceleracao, String zonaRecuperacao) {}

    /** Resultado da distribuição de delta: nova lista de etapas + delta restante. */
    private record DistribuicaoResult(List<EtapaTreinoLlmDto> etapas, double restante) {}

    private static String normalizarTipoEtapa(String tipoEtapa) {
        return tipoEtapa == null ? "" : tipoEtapa.trim().toUpperCase();
    }

    /**
     * Detecta etapas INTERVALADO comprimidas pelo LLM e as expande em etapas individuais.
     *
     * <p>Suporta dois padrões de compressão:</p>
     * <ul>
     *   <li><b>NxDist</b> (intervalados): "6x400m Z5" → 6× (INTERVALADO 0.4km + RECUPERACAO)</li>
     *   <li><b>Nx(Accel+Recov)</b> (fartlek): "4x (1min Z2 + 2min Z1)" → 4× (INTERVALADO 1min + RECUPERACAO 2min)</li>
     * </ul>
     */
    public TreinoPlanejadoLlmDto expandirEtapasAgregadas(TreinoPlanejadoLlmDto treino, List<ZonaFC> zonas) {
        if (treino.etapas() == null || treino.etapas().isEmpty()) return treino;

        List<EtapaTreinoLlmDto> etapas = treino.etapas();
        List<EtapaTreinoLlmDto> resultado = new ArrayList<>();
        boolean expandiu = false;

        int i = 0;
        while (i < etapas.size()) {
            EtapaTreinoLlmDto etapa = etapas.get(i);

            // O LLM tipa a série ora como INTERVALADO, ora como PRINCIPAL — ambos são válidos no
            // schema, e restringir a INTERVALADO fazia o fartlek escapar da expansão em silêncio.
            // Quem decide não é o tipo, é o padrão na descrição: PRINCIPAL sem padrão cai no
            // "nenhum padrão detectado" ao final do laço e é preservada intacta.
            if (!ETAPAS_EXPANSIVEIS.contains(normalizarTipoEtapa(etapa.tipoEtapa()))) {
                resultado.add(etapa);
                i++;
                continue;
            }

            // --- Caminho 1: padrão distância "6x400m" ---
            int n = detectarRepeticoesNaDescricao(etapa.descricaoEtapa());
            if (n > 1) {
                EtapaTreinoLlmDto recTemplate = recuperacaoAdjacenteOu(etapas, i + 1);
                if (recTemplate != null) i++;

                double distTiro = extrairDistanciaUnitariaDaDescricao(etapa.descricaoEtapa(), etapa.distanciaKm(), n);
                int durTiro   = Math.max(1, etapa.duracaoMin() != null ? etapa.duracaoMin() / n : 4);
                int durRec    = recTemplate != null && recTemplate.duracaoMin() != null
                        ? Math.max(1, recTemplate.duracaoMin() / n) : Math.max(1, durTiro / 2);
                double distRec = recTemplate != null && recTemplate.distanciaKm() != null
                        ? arredondar2(recTemplate.distanciaKm() / n) : arredondar2(distTiro * 0.4);
                String fcTiro = etapa.fcAlvoEtapa() != null ? etapa.fcAlvoEtapa() : "90-95% FCmax";
                String fcRec  = recTemplate != null && recTemplate.fcAlvoEtapa() != null
                        ? recTemplate.fcAlvoEtapa() : "60-70% FCmax";

                String ritmoTiro = etapa.ritmoAlvo();
                String ritmoRec  = recTemplate != null ? recTemplate.ritmoAlvo() : null;
                for (int rep = 1; rep <= n; rep++) {
                    // criação: sem record de origem (série comprimida vira N etapas novas)
                    resultado.add(new EtapaTreinoLlmDto(0, "INTERVALADO",
                            "Intervalo " + rep + "/" + n + " - Z5", durTiro, distTiro, fcTiro, 1, ritmoTiro));
                    resultado.add(new EtapaTreinoLlmDto(0, "RECUPERACAO",
                            "Recuperação " + rep + " - trote Z2", durRec, distRec, fcRec, 1, ritmoRec));
                }
                expandiu = true;
                log.info("EXPANSÃO NxDist [{}]: '{}' → {} tiros ({} etapas)",
                        treino.tipoTreino(), etapa.descricaoEtapa(), n, n * 2);
                i++;
                continue;
            }

            // --- Caminho 2: padrão tempo "4x (1min Z2 + 2min Z1)" ---
            FartlekParams fp = detectarFartlekNaDescricao(etapa.descricaoEtapa());
            if (fp != null) {
                EtapaTreinoLlmDto recTemplate = recuperacaoAdjacenteOu(etapas, i + 1);
                if (recTemplate != null) i++;

                // Distância vem do pace, não da distanciaKm da etapa de origem: a LLM costuma pôr ali a
                // distância do treino inteiro, e repartir 5 km por 5×(1min+2min) deu 1 km a cada 3min
                // (caso real 22/09). A recuperação nasce 0.0 e corrigir-temporais aplica o pace de trote.
                var paceAccel    = paceValidator.calcularPaceMedia(etapa.ritmoAlvo());
                double distAccel = paceAccel.isPresent() && paceAccel.getAsDouble() > 0
                        ? arredondar2(fp.duracaoAceleracao() / paceAccel.getAsDouble()) : 0.0;
                double distRecov = 0.0;

                String fcAccel = fp.zonaAceleracao() != null ? zonaParaFc(fp.zonaAceleracao(), zonas)
                        : (etapa.fcAlvoEtapa() != null ? etapa.fcAlvoEtapa() : "75-85% FCmax");
                String fcRecov = fp.zonaRecuperacao() != null ? zonaParaFc(fp.zonaRecuperacao(), zonas)
                        : (recTemplate != null && recTemplate.fcAlvoEtapa() != null
                                ? recTemplate.fcAlvoEtapa() : "60-70% FCmax");

                String ritmoAccel = etapa.ritmoAlvo();
                String ritmoRecov = recTemplate != null ? recTemplate.ritmoAlvo() : null;
                for (int rep = 1; rep <= fp.n(); rep++) {
                    // criação: sem record de origem (fartlek comprimido vira N pares aceleração+recuperação)
                    resultado.add(new EtapaTreinoLlmDto(0, "INTERVALADO",
                            "Aceleração " + rep + "/" + fp.n() + " - " + fp.duracaoAceleracao() + "min",
                            fp.duracaoAceleracao(), distAccel, fcAccel, 1, ritmoAccel));
                    resultado.add(new EtapaTreinoLlmDto(0, "RECUPERACAO",
                            "Recuperação " + rep + " - " + fp.duracaoRecuperacao() + "min trote",
                            fp.duracaoRecuperacao(), distRecov, fcRecov, 1, ritmoRecov));
                }
                expandiu = true;
                log.info("EXPANSÃO Fartlek [{}]: '{}' → {} acelerações ({} etapas)",
                        treino.tipoTreino(), etapa.descricaoEtapa(), fp.n(), fp.n() * 2);
                i++;
                continue;
            }

            // Nenhum padrão de compressão detectado
            resultado.add(etapa);
            i++;
        }

        if (!expandiu) return treino;
        // Só as etapas: a duração do treino fica com recalcular-duracao, que desempata pelo triângulo
        // pace×dist×dur — sobrescrever aqui escondia dele a duração que a LLM prescreveu.
        return treino.comEtapas(reordenarEtapas(resultado));
    }

    /** Retorna o próximo estágio se for RECUPERACAO, ou null caso contrário. */
    private EtapaTreinoLlmDto recuperacaoAdjacenteOu(List<EtapaTreinoLlmDto> etapas, int proximoIdx) {
        if (proximoIdx < etapas.size()
                && "RECUPERACAO".equalsIgnoreCase(etapas.get(proximoIdx).tipoEtapa())) {
            return etapas.get(proximoIdx);
        }
        return null;
    }

    /**
     * Extrai o número de repetições de padrões como "6x400m", "8 x 200", "5×1000m".
     * Retorna 1 se nenhum padrão for encontrado.
     */
    public int detectarRepeticoesNaDescricao(String descricao) {
        if (descricao == null || descricao.isBlank()) return 1;
        var matcher = REPETICOES_PATTERN.matcher(descricao);
        if (!matcher.find()) return 1;
        try {
            int n = Integer.parseInt(matcher.group(1));
            return (n >= 2 && n <= 20) ? n : 1; // sanity bounds
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Extrai a distância individual a partir da descrição (ex: "6x400m" → 0.4km).
     * Se não encontrar, divide o total por N.
     */
    public double extrairDistanciaUnitariaDaDescricao(String descricao, Double totalKm, int n) {
        if (descricao != null) {
            var matcher = REPETICOES_PATTERN.matcher(descricao);
            if (matcher.find()) {
                try {
                    double dist = Double.parseDouble(matcher.group(2));
                    String unidade = matcher.group(3);
                    if (unidade == null || "m".equalsIgnoreCase(unidade)) {
                        dist = dist / 1000.0; // metros → km
                    }
                    if (dist > 0 && dist <= 5.0) {
                        return arredondar2(dist);
                    }
                } catch (NumberFormatException ignored) { /* fallback abaixo */ }
            }
        }
        return (totalKm != null && totalKm > 0) ? arredondar2(totalKm / n) : 0.0;
    }

    private double arredondar2(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    /**
     * Deriva distanciaKm para etapas time-based (AQUECIMENTO, DESAQUECIMENTO, RECUPERACAO)
     * via duracaoMin ÷ paceZona, substituindo o valor incorreto gerado pelo LLM
     * (que usa o pace de tiro em vez do pace fácil).
     */
    public List<EtapaTreinoLlmDto> corrigirDistanciasEtapasTemporais(
            List<EtapaTreinoLlmDto> etapas, BigDecimal paceLimiar) {
        if (etapas == null || etapas.isEmpty()) return etapas;
        double paceZ2 = paceLimiar != null
                ? paceLimiar.doubleValue() * FATOR_PACE_Z2
                : PACE_Z2_DEFAULT_MIN_KM;
        double paceZ1 = paceLimiar != null
                ? paceLimiar.doubleValue() * FATOR_PACE_Z1
                : PACE_Z1_DEFAULT_MIN_KM;
        return etapas.stream()
                .map(e -> corrigirEtapaTemporal(e, paceZ1, paceZ2))
                .toList();
    }

    private EtapaTreinoLlmDto corrigirEtapaTemporal(EtapaTreinoLlmDto e, double paceZ1, double paceZ2) {
        if (e.tipoEtapa() == null) return e;
        if (e.duracaoMin() == null || e.duracaoMin() <= 0) return e;
        double pace = switch (e.tipoEtapa().toUpperCase()) {
            case "AQUECIMENTO", "DESAQUECIMENTO" -> paceZ2;
            case "RECUPERACAO" -> paceZ1;
            default -> -1.0;
        };
        if (pace <= 0) return e;
        return e.comDistancia(arredondar2(e.duracaoMin() / pace));
    }

    /**
     * Deriva a distância de cada etapa PRINCIPAL pelo pace: {@code duracaoMin ÷ pace médio do
     * ritmoAlvo} da própria etapa. A LLM concentra ali a distância do treino inteiro — caso real
     * 22/09: REGENERATIVO com PRINCIPAL de 5,5 km em 30min a 7:28-7:55/km (fix-etapas-continuos-pace).
     * Sem duração ou sem {@code ritmoAlvo} interpretável, a etapa fica como a LLM mandou.
     */
    public TreinoPlanejadoLlmDto distanciaPrincipalPorPace(TreinoPlanejadoLlmDto treino) {
        if (treino.etapas() == null || treino.etapas().isEmpty()) return treino;
        List<EtapaTreinoLlmDto> etapas = treino.etapas().stream().map(e -> {
            if (!"PRINCIPAL".equals(normalizarTipoEtapa(e.tipoEtapa()))) return e;
            if (e.duracaoMin() == null || e.duracaoMin() <= 0) return e;
            var pace = paceValidator.calcularPaceMedia(e.ritmoAlvo());
            if (pace.isEmpty() || pace.getAsDouble() <= 0) return e;
            return e.comDistancia(arredondar2(e.duracaoMin() / pace.getAsDouble()));
        }).toList();
        return treino.comEtapas(etapas);
    }

    /**
     * Deriva a distância de um treino CONTÍNUO cujas etapas nasceram sem distância (ex.: REGENERATIVO
     * sintetizado pelo reparo estrutural / substituição por lesão). Para cada etapa tempo-baseada sem
     * distância, calcula {@code duração / paceZ2} — inclusive a PRINCIPAL, que
     * {@code corrigirEtapaTemporal} deliberadamente ignora (nos estruturados a PRINCIPAL é distância
     * fixa). Não age em INTERVALADO/TIRO/FARTLEK nem sobrescreve distância já válida.
     */
    public TreinoPlanejadoLlmDto garantirDistanciaContinuo(TreinoPlanejadoLlmDto treino, BigDecimal paceLimiar) {
        if (treino.tipoTreino() == null || !TIPOS_CONTINUOS.contains(treino.tipoTreino())) return treino;
        if (treino.distanciaKm() != null && treino.distanciaKm() > 0) return treino;
        if (treino.etapas() == null || treino.etapas().isEmpty()) return treino;
        double pace = paceLimiar != null ? paceLimiar.doubleValue() * FATOR_PACE_Z2 : PACE_Z2_DEFAULT_MIN_KM;
        List<EtapaTreinoLlmDto> etapas = treino.etapas().stream().map(e -> {
            if (e.duracaoMin() == null || e.duracaoMin() <= 0) return e;
            if (e.distanciaKm() != null && e.distanciaKm() > 0) return e;
            return e.comDistancia(arredondar2(e.duracaoMin() / pace));
        }).toList();
        double total = somarDistancias(etapas);
        if (total <= 0) return treino;
        return treino.comEtapas(etapas).comDistancia(total);
    }

    /**
     * Detecta padrão "Nx (AccelMin ZoneA + RecovMin ZoneB)" em descrições de fartlek.
     * Exemplos: "4x (1min Z2 + 2min Z1)", "6 x (2min Z4 + 1min Z2)", "8x(3min+2min)".
     * Retorna null se o padrão não for encontrado.
     */
    private FartlekParams detectarFartlekNaDescricao(String descricao) {
        if (descricao == null || descricao.isBlank()) return null;
        var matcher = FARTLEK_TEMPO_PATTERN.matcher(descricao);
        if (!matcher.find()) return null;
        try {
            int n      = Integer.parseInt(matcher.group(1));
            int accel  = Integer.parseInt(matcher.group(2));
            int recov  = Integer.parseInt(matcher.group(3));
            if (n < 2 || n > 20 || accel < 1 || recov < 1) return null;

            // Tenta extrair zona da aceleração e recuperação do texto completo
            String zonaAccel = extrairZonaDaDescricao(descricao, matcher.start(2));
            String zonaRecov = extrairZonaDaDescricao(descricao, matcher.start(3));
            return new FartlekParams(n, accel, recov, zonaAccel, zonaRecov);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Procura a primeira ocorrência de "Z<N>" após a posição informada. */
    private String extrairZonaDaDescricao(String descricao, int aPartirDe) {
        var m = Pattern.compile("Z(\\d)(?:-Z(\\d))?", Pattern.CASE_INSENSITIVE)
                .matcher(descricao.substring(aPartirDe));
        return m.find() ? m.group(0).toUpperCase() : null;
    }

    /**
     * Converte índice de zona (0-based: 0=Z1 … 4=Z5) em string "fcMin-fcMax bpm".
     * Retorna null quando zonas é null ou o índice está fora do range.
     */
    // package-private (não private): testado diretamente, sem reflexão, por
    // TreinoNormalizadorZonaHelpersTest (mesmo pacote).
    String bpmDaZona(List<ZonaFC> zonas, int index) {
        if (zonas == null || index < 0 || index >= zonas.size()) return null;
        ZonaFC z = zonas.get(index);
        return z.fcMin() + "-" + z.fcMax() + " bpm";
    }

    /**
     * Converte "Z1"–"Z5" em range de FC.
     * Quando {@code zonas} não é null, retorna o range absoluto em bpm (formato que
     * {@code parseFcRange} consegue validar). Sem zonas, cai no fallback de percentual FCmax.
     */
    // package-private: ver bpmDaZona acima.
    String zonaParaFc(String zona, List<ZonaFC> zonas) {
        if (zona == null) return null;
        String z = zona.trim().toUpperCase();
        if (zonas != null) {
            if (z.startsWith("Z5")) return bpmDaZona(zonas, 4);
            if (z.startsWith("Z4")) return bpmDaZona(zonas, 3);
            if (z.startsWith("Z3")) return bpmDaZona(zonas, 2);
            if (z.startsWith("Z2")) return bpmDaZona(zonas, 1);
            if (z.startsWith("Z1")) return bpmDaZona(zonas, 0);
        }
        if (z.startsWith("Z5")) return "90-95% FCmax";
        if (z.startsWith("Z4")) return "80-90% FCmax";
        if (z.startsWith("Z3")) return "70-80% FCmax";
        if (z.startsWith("Z2")) return "65-75% FCmax";
        if (z.startsWith("Z1")) return "60-70% FCmax";
        return null;
    }

    /** Retorna novo record com duração recalculada e etapas atualizadas. */
    public TreinoPlanejadoLlmDto recalcularDuracaoTreino(TreinoPlanejadoLlmDto treino,
                                                          List<EtapaTreinoLlmDto> etapas) {
        int totalMin = somarDuracoesMin(etapas);
        String novaDuracao = String.format("%02d:00", totalMin);

        return treino.comDuracao(novaDuracao).comEtapas(etapas);
    }

    /**
     * Normaliza treino intervalado/tiro ajustando distâncias das etapas.
     * Abordagem puramente funcional: cria novas listas e records em cada passo.
     */
    public TreinoPlanejadoLlmDto normalizarTreinoIntervalado(TreinoPlanejadoLlmDto treino, NivelExperiencia nivel, List<ZonaFC> zonas) {
        if (!"INTERVALADO".equalsIgnoreCase(treino.tipoTreino()) && !"TIRO".equalsIgnoreCase(treino.tipoTreino())) {
            return treino;
        }

        if (treino.etapas() == null || treino.etapas().isEmpty()) return treino;

        double alvo = treino.distanciaKm() != null ? treino.distanciaKm() : 0.0;
        if (alvo <= 0.0) return treino;

        // Cópia mutável para trabalhar sem alterar o record original
        List<EtapaTreinoLlmDto> etapas = new ArrayList<>(treino.etapas());

        // 1) Ajustar aquecimento / desaquecimento para faixas fisiológicas
        etapas = clampDistanciaPorTipo(etapas, "AQUECIMENTO", 1.0, 2.0);
        etapas = clampDistanciaPorTipo(etapas, "DESAQUECIMENTO", 0.8, 1.5);

        // 2) Recalcular gap de distância
        double soma = somarDistancias(etapas);
        double gap = alvo - soma; // >0: faltando, <0: sobrando

        if (Math.abs(gap) > 0.05) {
            // 2.1) Se sobrar bastante distância e ainda dá pra ter mais tiros → adiciona tiro/rec
            int maxTiros = maxTirosPorNivel(nivel);
            int tirosAtuais = contarPorTipo(etapas, "INTERVALADO");

            while (gap > 0.6 && tirosAtuais < maxTiros) {
                etapas = adicionarTiroERecuperacao(etapas, 0.8, 0.3, 4, 2, zonas);
                tirosAtuais++;
                gap -= 1.1; // aproximado
            }

            // 2.2) Recalcular depois de adicionar tiros
            soma = somarDistancias(etapas);
            gap = alvo - soma;

            // 2.3) Distribuir delta restante nos tiros e recuperações
            //
            // Tiro nunca encolhe (gap<0): 800m/400m/1000m etc. são distâncias redondas, escolhidas
            // pela cinética de O2 — encolher o tiro para bater com o total declarado pelo LLM
            // descaracteriza o estímulo prescrito (ver proposal fix-intervalado-tiro-shrink-
            // normalizacao). A folga (RECUPERACAO) absorve a sobra; se não for suficiente,
            // reconciliarDistanciaComEtapas corrige a distanciaKm do treino pela soma real depois.
            // Crescer o tiro (gap>0, faltando volume) continua permitido — não quebra o estímulo.
            if (Math.abs(gap) > 0.05) {
                if (gap > 0) {
                    var resultadoTiros = distribuirDeltaPorTipo(etapas, "INTERVALADO", gap, 0.4, 1.2);
                    etapas = resultadoTiros.etapas();
                    gap = resultadoTiros.restante();
                }

                var resultadoRecs = distribuirDeltaPorTipo(etapas, "RECUPERACAO", gap, 0.2, 0.5);
                etapas = resultadoRecs.etapas();

                double somaFinal = somarDistancias(etapas);
                double deltaFinal = alvo - somaFinal;

                if (Math.abs(deltaFinal) > 0.2) {
                    log.warn("NORMALIZADOR: ainda há desvio de distância (alvo={} km, final={} km, delta={})",
                            alvo, somaFinal, deltaFinal);
                }
            }
        }

        // 3) Retornar novo record com etapas ajustadas e duração recalculada
        return recalcularDuracaoTreino(treino, etapas);
    }

    /**
     * Reconcilia distanciaKm do treino com a soma real das etapas geradas.
     *
     * <p>Após expansão de etapas (Fartlek, Intervalado), a distância declarada no nível
     * do treino pode divergir da soma das etapas individuais. Se o desvio for superior
     * a 10%, substitui distanciaKm pela soma das etapas (que representa a realidade).</p>
     *
     * <p>Só reconcilia quando <b>todas</b> as etapas têm distância: uma etapa em 0/null (a LLM pode
     * devolver {@code 0.0} quando não sabe calcular; "Fartlek livre" não é expandido) torna a soma
     * um piso, não um total — substituir a distância da LLM por ela gerou um FARTLEK de 2,64 km em
     * 50 min (fix-normalizador-etapas-incompletas). Sem distância da LLM, a soma continua sendo o
     * melhor valor disponível.</p>
     */
    public TreinoPlanejadoLlmDto reconciliarDistanciaComEtapas(TreinoPlanejadoLlmDto treino) {
        if (treino.etapas() == null || treino.etapas().isEmpty()) return treino;

        double somaEtapas = somarDistancias(treino.etapas());
        double distanciaAtual = treino.distanciaKm() != null ? treino.distanciaKm() : 0.0;

        if (distanciaAtual <= 0) {
            log.info("RECONCILIAÇÃO [{}]: distanciaKm não definida → usando soma das etapas: {} km",
                    treino.tipoTreino(), somaEtapas);
            return treino.comDistancia(somaEtapas);
        }

        long etapasSemDistancia = treino.etapas().stream()
                .filter(e -> e.distanciaKm() == null || e.distanciaKm() <= 0)
                .count();
        if (etapasSemDistancia > 0) {
            log.warn("RECONCILIAÇÃO [{}]: {} etapa(s) sem distância (soma parcial {} km) → mantendo distanciaKm={} km da LLM",
                    treino.tipoTreino(), etapasSemDistancia, String.format("%.2f", somaEtapas), distanciaAtual);
            return treino;
        }

        double desvioPercent = Math.abs(somaEtapas - distanciaAtual) / distanciaAtual;
        if (desvioPercent > 0.10) {
            log.warn("RECONCILIAÇÃO [{}]: distanciaKm={} km, soma_etapas={} km → desvio {}% > 10%, reconciliando",
                    treino.tipoTreino(), distanciaAtual, String.format("%.2f", somaEtapas),
                    Math.round(desvioPercent * 100));
            return treino.comDistancia(somaEtapas);
        }

        return treino;
    }

    private double somarDistancias(List<EtapaTreinoLlmDto> etapas) {
        return etapas.stream()
                .mapToDouble(e -> e.distanciaKm() != null ? e.distanciaKm() : 0.0)
                .sum();
    }

    public int somarDuracoesMin(List<EtapaTreinoLlmDto> etapas) {
        return etapas.stream()
                .mapToInt(e -> e.duracaoMin() != null ? e.duracaoMin() : 0)
                .sum();
    }

    private int contarPorTipo(List<EtapaTreinoLlmDto> etapas, String tipo) {
        return (int) etapas.stream()
                .filter(e -> tipo.equalsIgnoreCase(e.tipoEtapa()))
                .count();
    }

    /** Retorna nova lista com distâncias clamped para etapas do tipo especificado. */
    private List<EtapaTreinoLlmDto> clampDistanciaPorTipo(List<EtapaTreinoLlmDto> etapas,
                                                           String tipo,
                                                           double min, double max) {
        return etapas.stream().map(e -> {
            if (!tipo.equalsIgnoreCase(e.tipoEtapa())) return e;
            Double d = e.distanciaKm();
            if (d == null || d <= 0) return e;
            if (d >= min && d <= max) return e;

            double novaDistancia = Math.max(min, Math.min(max, d));
            return e.comDistancia(novaDistancia)
                    .comDuracao(recalcularDuracaoDePace(e.ritmoAlvo(), novaDistancia, e.duracaoMin()));
        }).collect(Collectors.toList());
    }

    /**
     * IA-05 (review.md 2026-09-05): quando a etapa tem {@code ritmoAlvo}, ele é a fonte de
     * verdade — qualquer ajuste de {@code distanciaKm} feito por {@code clampDistanciaPorTipo}/
     * {@code distribuirDeltaPorTipo} precisa recalcular {@code duracaoMin} a partir do pace médio,
     * senão a etapa fica com distância nova e duração antiga (inconsistente). Sem
     * {@code ritmoAlvo}, não há pace pra recalcular a partir dele — {@code duracaoMin} original é
     * preservado.
     */
    private Integer recalcularDuracaoDePace(String ritmoAlvo, double distanciaKm, Integer duracaoMinOriginal) {
        if (ritmoAlvo == null) return duracaoMinOriginal;
        var paceMedia = paceValidator.calcularPaceMedia(ritmoAlvo);
        if (paceMedia.isEmpty()) return duracaoMinOriginal;
        return (int) Math.round(distanciaKm * paceMedia.getAsDouble());
    }

    /**
     * Distribui delta de distância entre etapas do tipo especificado.
     * Retorna nova lista completa + delta restante.
     */
    private DistribuicaoResult distribuirDeltaPorTipo(List<EtapaTreinoLlmDto> etapas,
                                                      String tipo,
                                                      double delta,
                                                      double min, double max) {
        List<EtapaTreinoLlmDto> resultado = new ArrayList<>(etapas);
        long count = resultado.stream().filter(e -> tipo.equalsIgnoreCase(e.tipoEtapa())).count();
        if (count == 0) return new DistribuicaoResult(resultado, delta);

        double restante = delta;

        for (int round = 0; round < 3 && Math.abs(restante) > 0.01; round++) {
            double passo = restante / count;

            for (int i = 0; i < resultado.size(); i++) {
                if (Math.abs(restante) < 0.01) break;

                EtapaTreinoLlmDto e = resultado.get(i);
                if (!tipo.equalsIgnoreCase(e.tipoEtapa())) continue;

                double atual = e.distanciaKm() != null ? e.distanciaKm() : 0.0;
                double proposto = atual + passo;

                double novo;
                if (restante > 0) {
                    novo = Math.min(proposto, max);
                } else {
                    novo = Math.max(proposto, min);
                }

                double aplicado = novo - atual;
                if ((restante > 0 && aplicado > 0) || (restante < 0 && aplicado < 0)) {
                    double novaDistancia = atual + aplicado;
                    resultado.set(i, e.comDistancia(novaDistancia)
                            .comDuracao(recalcularDuracaoDePace(e.ritmoAlvo(), novaDistancia, e.duracaoMin())));
                    restante -= aplicado;
                }
            }
        }

        return new DistribuicaoResult(resultado, restante);
    }

    private int maxTirosPorNivel(NivelExperiencia nivel) {
        if (nivel == null) return 5; // default

        return switch (nivel) {
            case INICIANTE      -> 4;
            case INTERMEDIARIO  -> 5;
            case AVANCADO       -> 7;
            case ELITE          -> 10;
        };
    }

    /** Retorna nova lista com tiro e recuperação inseridos antes do desaquecimento. */
    // package-private: ver bpmDaZona acima.
    List<EtapaTreinoLlmDto> adicionarTiroERecuperacao(List<EtapaTreinoLlmDto> etapas,
                                                               double distTiro,
                                                               double distRec,
                                                               int duracaoTiroMin,
                                                               int duracaoRecMin,
                                                               List<ZonaFC> zonas) {
        if (etapas.isEmpty()) return etapas;

        String fcTiro = bpmDaZona(zonas, 4); // Z5
        if (fcTiro == null) fcTiro = "90-95% FCmax";
        String fcRec = bpmDaZona(zonas, 0); // Z1
        if (fcRec == null) fcRec = "70-80% FCmax";

        List<EtapaTreinoLlmDto> resultado = new ArrayList<>(etapas);

        // Inserir antes do desaquecimento (ou no fim)
        int idxDesaq = -1;
        for (int i = 0; i < resultado.size(); i++) {
            if ("DESAQUECIMENTO".equalsIgnoreCase(resultado.get(i).tipoEtapa())) {
                idxDesaq = i;
                break;
            }
        }
        int insertIndex = (idxDesaq >= 0) ? idxDesaq : resultado.size();

        // criação: sem record de origem (par tiro+recuperação sintetizado pra fechar a distância)
        resultado.add(insertIndex, new EtapaTreinoLlmDto(
                0, "INTERVALADO", "Tiro extra em Z5",
                duracaoTiroMin, distTiro, fcTiro, 1, null
        ));
        resultado.add(insertIndex + 1, new EtapaTreinoLlmDto(
                0, "RECUPERACAO", "Recuperação extra em Z2",
                duracaoRecMin, distRec, fcRec, 1, null
        ));

        // Reordenar ordens 1..N
        return reordenarEtapas(resultado);
    }

    /** Retorna nova lista com ordens sequenciais 1..N. */
    private List<EtapaTreinoLlmDto> reordenarEtapas(List<EtapaTreinoLlmDto> etapas) {
        List<EtapaTreinoLlmDto> resultado = new ArrayList<>(etapas.size());
        for (int i = 0; i < etapas.size(); i++) {
            resultado.add(etapas.get(i).comOrdem(i + 1));
        }
        return resultado;
    }
}
