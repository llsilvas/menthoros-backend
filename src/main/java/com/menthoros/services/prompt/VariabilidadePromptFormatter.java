package com.menthoros.services.prompt;

import com.menthoros.entity.TreinoRealizado;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Formatador de variabilidade e estímulos de treino para prompts de IA.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Análise de estímulos recentes (tipos, gaps, volume, intensidade)</li>
 *   <li>Matriz de variabilidade de intervalados (categorias A-E)</li>
 *   <li>Alertas de variabilidade (repetição, gaps, frequência)</li>
 * </ul>
 */
@Component
public class VariabilidadePromptFormatter {

    private static final List<String> TIPOS_ESPERADOS = Arrays.asList(
            "REGENERATIVO", "CONTINUO", "INTERVALADO", "LONGO", "TEMPO_RUN", "FARTLEK"
    );

    /**
     * Analisa estímulos recentes e retorna análise formatada para o prompt.
     * Recebe treinos pré-carregados (sem acesso a repository).
     */
    public String analisarEstimulosRecentes(List<TreinoRealizado> treinosRecentes, LocalDate dataReferencia) {
        if (treinosRecentes == null || treinosRecentes.isEmpty()) {
            return "**ANÁLISE PRÉ-PLANEJAMENTO:**\n\n" +
                    "❌ Nenhum treino realizado nos últimos 28 dias.\n" +
                    "⚠️ RECOMENDAÇÃO: Iniciar com volume conservador (progressão lenta).\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**ANÁLISE PRÉ-PLANEJAMENTO (últimas 4 semanas):**\n\n");

        sb.append("### 1. Padrão de Estímulos\n");
        analisarTiposTreinoEGaps(treinosRecentes, dataReferencia, sb);

        sb.append("\n### 2. Volume Semanal Realizado\n");
        analisarVolumeSemanal(treinosRecentes, dataReferencia, sb);

        sb.append("\n### 3. Padrão de Intensidade (Distribuição de Zonas)\n");
        analisarIntensidadeZonas(treinosRecentes, sb);

        sb.append("\n### 4. Sinais de Fadiga/Sobrecarga\n");
        analisarSobreCarga(treinosRecentes, dataReferencia, sb);

        sb.append("\n---\n");
        return sb.toString();
    }

    /**
     * Identifica a matriz de variabilidade de intervalados (últimas 4 semanas)
     * e recomenda qual categoria usar esta semana.
     *
     * <p>Categorias de Intervalado:</p>
     * <ul>
     *   <li>A: VO2max curto (200m, 400m, 600m)</li>
     *   <li>B: VO2max longo (3-5 min)</li>
     *   <li>C: Threshold (4-6 min no pace limiar)</li>
     *   <li>D: Tempo Run (contínuo em Z3)</li>
     *   <li>E: Fartlek estruturado (variado)</li>
     * </ul>
     */
    public String identificarMatrizVariabilidade(List<TreinoRealizado> treinos, LocalDate dataReferencia) {
        if (treinos == null || treinos.isEmpty()) {
            return """
                    **MATRIZ DE VARIABILIDADE:**

                    ⚠️ Nenhum treino realizado nos últimos 28 dias.
                    ✅ RECOMENDAÇÃO: Começar com INTERVALADO Categoria A ou B (VO2max)
                    """;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**MATRIZ DE VARIABILIDADE (últimas 4 semanas):**\n\n");

        Map<Integer, String> categoriaPorSemana = new HashMap<>();
        Map<Integer, String> descricaoPorSemana = new HashMap<>();

        for (int semana = 0; semana < 4; semana++) {
            LocalDate fimSemana = dataReferencia.minusWeeks(semana);
            LocalDate inicioSem = fimSemana.minusDays(6);

            TreinoRealizado treinoIntervalado = treinos.stream()
                    .filter(t -> !t.getDataTreino().isBefore(inicioSem) &&
                            !t.getDataTreino().isAfter(fimSemana) &&
                            t.getTipoTreino() != null &&
                            (t.getTipoTreino().toString().contains("INTERVALADO") ||
                                    t.getTipoTreino().toString().contains("FARTLEK")))
                    .findFirst()
                    .orElse(null);

            String categoria = "NENHUM";
            String descricao = "Sem intervalado";

            if (treinoIntervalado != null) {
                categoria = identificarCategoriaIntervalado(treinoIntervalado);
                descricao = treinoIntervalado.getObservacao() != null ?
                        treinoIntervalado.getObservacao().substring(0, Math.min(50, treinoIntervalado.getObservacao().length())) :
                        "Intervalado";
            }

            categoriaPorSemana.put(semana, categoria);
            descricaoPorSemana.put(semana, descricao);

            String semanaLabel = switch (semana) {
                case 0 -> "Semana atual (hoje -0 dias)";
                case 1 -> "Semana passada (hoje -7 dias)";
                case 2 -> "2 semanas atrás (hoje -14 dias)";
                case 3 -> "3 semanas atrás (hoje -21 dias)";
                default -> "Semana " + semana;
            };

            sb.append(String.format("**Semana %d:** %s\n", semana, semanaLabel));
            sb.append(String.format("  └─ Categoria: %s | %s\n\n", categoria, descricao));
        }

        String recomendacao = recomendarCategoriaIntervalado(categoriaPorSemana);
        sb.append("✅ **RECOMENDAÇÃO PARA ESTA SEMANA:**\n");
        sb.append(recomendacao);

        String avisos = gerarAvisosRepetidosIntervalados(categoriaPorSemana);
        if (!avisos.isEmpty()) {
            sb.append("\n⚠️ **AVISOS DE REPETIÇÃO:**\n");
            sb.append(avisos);
        }

        sb.append("\n---\n");
        return sb.toString();
    }

    /**
     * Gera alertas de variabilidade de treinos (repetição, gaps, frequência).
     */
    public String gerarAlertasVariabilidade(List<TreinoRealizado> treinos, LocalDate dataReferencia) {
        if (treinos == null || treinos.isEmpty()) {
            return "✅ Sem alertas de variabilidade (nenhum treino realizado).\n";
        }

        StringBuilder alertas = new StringBuilder();
        boolean temAlertas = false;

        // ALERTA 1: Tipos de treino ausentes há >14 dias
        alertas.append("### Estímulos Ausentes Há Mais de 14 Dias\n");
        Map<String, LocalDate> ultimaDataPorTipo = extrairUltimaDataPorTipo(treinos);

        for (String tipo : TIPOS_ESPERADOS) {
            if (!ultimaDataPorTipo.containsKey(tipo)) {
                alertas.append(String.format("🔴 **%s:** NUNCA realizado - REINTRODUZIR URGENTEMENTE\n", tipo));
                temAlertas = true;
            } else {
                LocalDate ultimaData = ultimaDataPorTipo.get(tipo);
                long diasDesde = ChronoUnit.DAYS.between(ultimaData, dataReferencia);

                if (diasDesde > 14 && diasDesde <= 21) {
                    alertas.append(String.format("🟡 **%s:** Ausente há %d dias (considerar reintroduzir)\n", tipo, diasDesde));
                    temAlertas = true;
                } else if (diasDesde > 21) {
                    alertas.append(String.format("🔴 **%s:** Ausente há %d dias (REINTRODUZIR ESTA SEMANA)\n", tipo, diasDesde));
                    temAlertas = true;
                }
            }
        }

        if (!temAlertas) {
            alertas.append("✅ Nenhum estímulo ausente há >14 dias\n");
        }

        // ALERTA 2: Repetição de mesma categoria de intervalado
        alertas.append("\n### Repetição de Categorias de Intervalado\n");
        Map<Integer, String> categoriaPorSemana = extrairCategoriasPorSemana(treinos, dataReferencia);
        boolean temRepetidos = false;

        String cat0 = categoriaPorSemana.getOrDefault(0, "NENHUM");
        String cat1 = categoriaPorSemana.getOrDefault(1, "NENHUM");
        String cat2 = categoriaPorSemana.getOrDefault(2, "NENHUM");

        if (!cat0.equals("NENHUM") && cat0.equals(cat1)) {
            alertas.append(String.format("🟡 **Categoria repetida:** %s está sendo usada 2 semanas consecutivas\n", cat0));
            alertas.append("   → Recomendação: ALTERNAR para outra categoria esta semana\n");
            temRepetidos = true;
        }

        if (!cat1.equals("NENHUM") && cat1.equals(cat2)) {
            alertas.append(String.format("⚠️ **Padrão observado:** %s também foi usado há 2 semanas\n", cat1));
            temRepetidos = true;
        }

        if (!temRepetidos && !cat0.equals("NENHUM")) {
            alertas.append("✅ Boa rotação entre categorias de intervalado\n");
        }

        // ALERTA 3: Gaps entre treinos de qualidade
        alertas.append("\n### Frequência de Treinos Intensivos\n");
        long treinosIntensivos = treinos.stream()
                .filter(t -> t.getTipoTreino() != null &&
                        (t.getTipoTreino().toString().contains("INTERVALADO") ||
                                t.getTipoTreino().toString().contains("TEMPO_RUN") ||
                                t.getTipoTreino().toString().contains("FARTLEK")))
                .count();

        long totalTreinos = treinos.size();
        double pctIntensivos = totalTreinos > 0 ? (treinosIntensivos * 100.0 / totalTreinos) : 0;

        if (pctIntensivos < 15) {
            alertas.append(String.format("🟡 Baixa frequência de treinos intensivos (%.0f%% dos treinos)\n", pctIntensivos));
            alertas.append("   → Considerar aumentar frequência de intervalados/tempo runs\n");
        } else if (pctIntensivos > 40) {
            alertas.append(String.format("🟡 Alta frequência de treinos intensivos (%.0f%% dos treinos)\n", pctIntensivos));
            alertas.append("   → Considerar aumentar treinos regenerativos para equilíbrio\n");
        } else {
            alertas.append(String.format("✅ Frequência adequada de intensivos (%.0f%% dos treinos)\n", pctIntensivos));
        }

        // ALERTA 4: Variabilidade geral
        alertas.append("\n### Variabilidade Geral de Treinos\n");
        long tiposDiferentes = ultimaDataPorTipo.size();

        if (tiposDiferentes >= 5) {
            alertas.append("✅ Excelente variabilidade - treinos de múltiplos tipos sendo realizados\n");
        } else if (tiposDiferentes >= 3) {
            alertas.append("🟡 Variabilidade moderada - considerar incluir mais tipos de treino\n");
        } else {
            alertas.append("🔴 Variabilidade baixa - apenas " + tiposDiferentes + " tipo(s) de treino realizado(s)\n");
            alertas.append("   → Adicionar treinos regenerativos e/ou variações de intensidade\n");
        }

        return alertas.toString();
    }

    // --- Métodos privados ---

    private void analisarTiposTreinoEGaps(List<TreinoRealizado> treinos, LocalDate dataReferencia, StringBuilder sb) {
        Map<String, LocalDate> ultimaDataPorTipo = extrairUltimaDataPorTipo(treinos);

        sb.append("**Tipos de treino realizados:**\n");
        TIPOS_ESPERADOS.forEach(tipo -> {
            if (ultimaDataPorTipo.containsKey(tipo)) {
                LocalDate ultima = ultimaDataPorTipo.get(tipo);
                long diasDesde = ChronoUnit.DAYS.between(ultima, dataReferencia);

                if (diasDesde <= 7) {
                    sb.append(String.format("- ✅ %s: realizado há %d dias%n", tipo, diasDesde));
                } else if (diasDesde <= 14) {
                    sb.append(String.format("- 🟡 %s: realizado há %d dias (considerar reintroduzir)%n", tipo, diasDesde));
                } else {
                    sb.append(String.format("- 🔴 %s: ausente há %d dias (REINTRODUZIR ESTA SEMANA)%n", tipo, diasDesde));
                }
            } else {
                sb.append(String.format("- ⚠️ %s: NUNCA realizado%n", tipo));
            }
        });
    }

    private void analisarVolumeSemanal(List<TreinoRealizado> treinos, LocalDate dataReferencia, StringBuilder sb) {
        for (int semana = 0; semana < 3; semana++) {
            LocalDate fimSemana = dataReferencia.minusWeeks(semana);
            LocalDate inicioSemana = fimSemana.minusDays(6);

            BigDecimal volumeSemana = treinos.stream()
                    .filter(t -> !t.getDataTreino().isBefore(inicioSemana) &&
                            !t.getDataTreino().isAfter(fimSemana))
                    .map(t -> t.getDistanciaKm() != null ? t.getDistanciaKm() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            int tssSemana = treinos.stream()
                    .filter(t -> !t.getDataTreino().isBefore(inicioSemana) &&
                            !t.getDataTreino().isAfter(fimSemana))
                    .mapToInt(t -> t.getTssCalculado() != null ? t.getTssCalculado() : 0)
                    .sum();

            long treinosSemana = treinos.stream()
                    .filter(t -> !t.getDataTreino().isBefore(inicioSemana) &&
                            !t.getDataTreino().isAfter(fimSemana))
                    .count();

            String label = switch (semana) {
                case 0 -> "📊 Atual (0–6 dias atrás)";
                case 1 -> "📈 Anterior (7–13 dias atrás)";
                case 2 -> "📊 Base (14–20 dias atrás)";
                default -> "Semana " + semana;
            };
            sb.append(String.format("- **Semana %s:** %.1f km | %d TSS | %d treinos%n",
                    label, volumeSemana.doubleValue(), tssSemana, treinosSemana));
        }
    }

    private void analisarIntensidadeZonas(List<TreinoRealizado> treinos, StringBuilder sb) {
        int tssZ1 = 0, tssZ2 = 0, tssZ3 = 0, tssZ4 = 0, tssZ5 = 0;
        int totalTss = 0;

        for (TreinoRealizado t : treinos) {
            Integer tss = t.getTssCalculado() != null ? t.getTssCalculado() : 0;
            totalTss += tss;

            String zona = t.getZonaAlvo() != null ? t.getZonaAlvo().toUpperCase() : "DESCONHECIDA";

            if (zona.contains("Z1")) {
                tssZ1 += tss;
            } else if (zona.contains("Z2")) {
                tssZ2 += tss;
            } else if (zona.contains("Z3")) {
                tssZ3 += tss;
            } else if (zona.contains("Z4")) {
                tssZ4 += tss;
            } else if (zona.contains("Z5")) {
                tssZ5 += tss;
            }
        }

        double pctZ1 = totalTss > 0 ? (tssZ1 * 100.0 / totalTss) : 0;
        double pctZ2 = totalTss > 0 ? (tssZ2 * 100.0 / totalTss) : 0;
        double pctZ3 = totalTss > 0 ? (tssZ3 * 100.0 / totalTss) : 0;
        double pctZ4 = totalTss > 0 ? (tssZ4 * 100.0 / totalTss) : 0;
        double pctZ5 = totalTss > 0 ? (tssZ5 * 100.0 / totalTss) : 0;

        sb.append(String.format("- **Z1 (Recuperação):** %.0f TSS (%.0f%%)%n", (double) tssZ1, pctZ1));
        sb.append(String.format("- **Z2 (Base Aeróbica):** %.0f TSS (%.0f%%) %s%n",
                (double) tssZ2, pctZ2, pctZ2 >= 50 ? "✅" : "⚠️"));
        sb.append(String.format("- **Z3 (Contínuo Moderado):** %.0f TSS (%.0f%%)%n", (double) tssZ3, pctZ3));
        sb.append(String.format("- **Z4 (Threshold):** %.0f TSS (%.0f%%)%n", (double) tssZ4, pctZ4));
        sb.append(String.format("- **Z5 (VO2max):** %.0f TSS (%.0f%%)%n", (double) tssZ5, pctZ5));
    }

    private void analisarSobreCarga(List<TreinoRealizado> treinos, LocalDate dataReferencia, StringBuilder sb) {
        long treinos14dias = treinos.stream()
                .filter(t -> !t.getDataTreino().isBefore(dataReferencia.minusDays(14)))
                .count();

        double rpeMedia = treinos.stream()
                .filter(t -> t.getPercepcaoEsforco() != null)
                .mapToInt(TreinoRealizado::getPercepcaoEsforco)
                .average()
                .orElse(0);

        sb.append(String.format("- **Treinos nos últimos 14 dias:** %d%n", treinos14dias));
        sb.append(String.format("- **RPE médio:** %.1f/10 %s%n",
                rpeMedia,
                rpeMedia >= 7 ? "⚠️ (atleta relatando esforço elevado)" : "✅"));

        long treinosIntensivos = treinos.stream()
                .filter(t -> t.getPercepcaoEsforco() != null && t.getPercepcaoEsforco() >= 8)
                .count();

        if (treinosIntensivos > treinos14dias * 0.5) {
            sb.append("- 🔴 Mais de 50% dos treinos com RPE ≥8 (REDUZIR INTENSIDADE ESTA SEMANA)\n");
        } else if (treinosIntensivos > treinos14dias * 0.3) {
            sb.append("- 🟡 Entre 30-50% com alta intensidade (monitorar sinais de fadiga)\n");
        } else {
            sb.append("- ✅ Distribuição de intensidade adequada\n");
        }
    }

    private String identificarCategoriaIntervalado(TreinoRealizado treino) {
        String obs = treino.getObservacao() != null ? treino.getObservacao().toUpperCase() : "";
        String tipo = treino.getTipoTreino() != null ? treino.getTipoTreino().toString() : "";

        if (obs.contains("200M") || obs.contains("400M") || obs.contains("CURTO")) {
            return "A (VO2max curto)";
        } else if (obs.contains("3MIN") || obs.contains("4MIN") || obs.contains("5MIN") || obs.contains("LONGO")) {
            return "B (VO2max longo)";
        } else if (obs.contains("THRESHOLD") || obs.contains("LIMIAR") || obs.contains("4-6 MIN")) {
            return "C (Threshold)";
        } else if (obs.contains("TEMPO") || obs.contains("CONTÍNUO") || obs.contains("Z3")) {
            return "D (Tempo Run)";
        } else if (tipo.contains("FARTLEK") || obs.contains("FARTLEK") || obs.contains("VARIADO")) {
            return "E (Fartlek)";
        }

        return "Indeterminada";
    }

    private String recomendarCategoriaIntervalado(Map<Integer, String> categoriaPorSemana) {
        String categoriaUltimaSemana = categoriaPorSemana.getOrDefault(1, "NENHUM");
        String categoriaSemanaAtual = categoriaPorSemana.getOrDefault(0, "NENHUM");

        if (categoriaSemanaAtual.equals("NENHUM") || categoriaSemanaAtual.equals("Indeterminada")) {
            return switch (categoriaUltimaSemana) {
                case "A (VO2max curto)" ->
                        "→ Use **Categoria B** (VO2max longo - 3-5 min)\n  Razão: Alternar com semana anterior (A→B)";
                case "B (VO2max longo)" ->
                        "→ Use **Categoria C** (Threshold - 4-6 min)\n  Razão: Alternar com semana anterior (B→C)";
                case "C (Threshold)" ->
                        "→ Use **Categoria D** (Tempo Run - Z3)\n  Razão: Alternar com semana anterior (C→D)";
                case "D (Tempo Run)" ->
                        "→ Use **Categoria A** (VO2max curto - 200m/400m)\n  Razão: Alternar com semana anterior (D→A)";
                case "E (Fartlek)" -> "→ Use **Categoria A** (VO2max curto)\n  Razão: Alternar com Fartlek (E→A)";
                default -> "→ Use **Categoria A** (VO2max curto - 200m/400m)\n  Razão: Começar com base sólida";
            };
        } else {
            return switch (categoriaSemanaAtual) {
                case "A (VO2max curto)" ->
                        "→ Considerou **Categoria B** (VO2max longo) na próxima\n  Razão: Progressão natural (A→B)";
                case "B (VO2max longo)" ->
                        "→ Considerou **Categoria C** (Threshold) na próxima\n  Razão: Progressão natural (B→C)";
                case "C (Threshold)" ->
                        "→ Considerou **Categoria D** (Tempo Run) na próxima\n  Razão: Progressão natural (C→D)";
                case "D (Tempo Run)" -> "→ Use **Categoria A** (VO2max curto)\n  Razão: Volta ao ciclo (D→A)";
                case "E (Fartlek)" -> "→ Use **Categoria B** (VO2max longo)\n  Razão: Alternar após Fartlek (E→B)";
                default -> "→ Use **Categoria A** (VO2max curto)\n  Razão: Recomendação padrão";
            };
        }
    }

    private String gerarAvisosRepetidosIntervalados(Map<Integer, String> categoriaPorSemana) {
        StringBuilder avisos = new StringBuilder();

        String categoriaSemanaAtual = categoriaPorSemana.getOrDefault(0, "NENHUM");
        String categoriaUltimaSemana = categoriaPorSemana.getOrDefault(1, "NENHUM");

        if (!categoriaSemanaAtual.equals("NENHUM") &&
                !categoriaSemanaAtual.equals("Indeterminada") &&
                categoriaSemanaAtual.equals(categoriaUltimaSemana)) {
            avisos.append(String.format("🟡 Mesma categoria (%s) em 2 semanas consecutivas\n", categoriaSemanaAtual));
            avisos.append("   → MUDAR para outra categoria esta semana\n\n");
        }

        boolean temAlgumIntervalado = false;
        for (int i = 0; i < 4; i++) {
            String cat = categoriaPorSemana.getOrDefault(i, "NENHUM");
            if (!cat.equals("NENHUM") && !cat.equals("Indeterminada")) {
                temAlgumIntervalado = true;
                break;
            }
        }

        if (!temAlgumIntervalado) {
            // Nenhum intervalado nas últimas 4 semanas - não há o que avaliar
        } else if (categoriaPorSemana.values().stream()
                .noneMatch(c -> c.equals("NENHUM") || c.equals("Indeterminada"))) {
            avisos.append("✅ Boa rotação de categorias observada nos últimos 28 dias\n");
        }

        return avisos.toString();
    }

    private Map<String, LocalDate> extrairUltimaDataPorTipo(List<TreinoRealizado> treinos) {
        Map<String, LocalDate> mapa = new HashMap<>();

        treinos.forEach(t -> {
            String tipo = t.getTipoTreino() != null ? t.getTipoTreino().toString() : "DESCONHECIDO";
            LocalDate ultimaData = mapa.getOrDefault(tipo, t.getDataTreino());

            if (t.getDataTreino().isAfter(ultimaData)) {
                mapa.put(tipo, t.getDataTreino());
            } else {
                mapa.put(tipo, ultimaData);
            }
        });

        return mapa;
    }

    private Map<Integer, String> extrairCategoriasPorSemana(List<TreinoRealizado> treinos, LocalDate dataReferencia) {
        Map<Integer, String> categorias = new HashMap<>();

        for (int semana = 0; semana < 4; semana++) {
            LocalDate fimSemana = dataReferencia.minusWeeks(semana);
            LocalDate inicioSem = fimSemana.minusDays(6);

            TreinoRealizado treinoIntervalado = treinos.stream()
                    .filter(t -> !t.getDataTreino().isBefore(inicioSem) &&
                            !t.getDataTreino().isAfter(fimSemana) &&
                            t.getTipoTreino() != null &&
                            (t.getTipoTreino().toString().contains("INTERVALADO") ||
                                    t.getTipoTreino().toString().contains("FARTLEK")))
                    .findFirst()
                    .orElse(null);

            if (treinoIntervalado != null) {
                String categoria = identificarCategoriaIntervalado(treinoIntervalado);
                categorias.put(semana, categoria);
            } else {
                categorias.put(semana, "NENHUM");
            }
        }

        return categorias;
    }

    /**
     * Calcula volume médio das últimas 3 semanas com análise de tendência.
     * Recebe treinos pré-carregados (últimas 3 semanas).
     */
    public Map<String, Object> calcularVolumeMedioUltimasTresSemanas(List<TreinoRealizado> treinos, LocalDate dataReferencia) {
        Map<String, Object> resultado = new HashMap<>();

        if (treinos == null || treinos.isEmpty()) {
            resultado.put("volumeMedioKm", 0.0);
            resultado.put("volumeMinimoKm", 0.0);
            resultado.put("volumeMaximoKm", 0.0);
            resultado.put("tendencia", "SEM DADOS");
            resultado.put("tssMedioPorSemana", 0);
            resultado.put("treinosPorSemana", 0.0);
            resultado.put("volumeSemanaMaisRecente", 0.0);
            resultado.put("volumeSemanaAnterior", 0.0);
            resultado.put("volumeDuasSemanas", 0.0);
            return resultado;
        }

        List<Double> volumesPorSemana = new java.util.ArrayList<>();
        List<Integer> tssPorSemana = new java.util.ArrayList<>();
        List<Long> treinosPorSemanaList = new java.util.ArrayList<>();

        for (int semana = 0; semana < 3; semana++) {
            LocalDate fimSemana = dataReferencia.minusWeeks(semana);
            LocalDate inicioSemana = fimSemana.minusDays(6);

            List<TreinoRealizado> treinosSemana = treinos.stream()
                    .filter(t -> !t.getDataTreino().isBefore(inicioSemana) &&
                            !t.getDataTreino().isAfter(fimSemana))
                    .toList();

            BigDecimal volumeSemana = treinosSemana.stream()
                    .map(t -> t.getDistanciaKm() != null ? t.getDistanciaKm() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            int tssSemana = treinosSemana.stream()
                    .mapToInt(t -> t.getTssCalculado() != null ? t.getTssCalculado() : 0)
                    .sum();

            volumesPorSemana.add(volumeSemana.doubleValue());
            tssPorSemana.add(tssSemana);
            treinosPorSemanaList.add((long) treinosSemana.size());
        }

        double volumeTotal = volumesPorSemana.stream().mapToDouble(Double::doubleValue).sum();
        double volumeMedio = volumeTotal / 3.0;
        double volumeMinimo = volumesPorSemana.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double volumeMaximo = volumesPorSemana.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        int tssMedioSemanal = (int) tssPorSemana.stream().mapToInt(Integer::intValue).average().orElse(0);
        double treinosMedioSemanal = treinosPorSemanaList.stream().mapToLong(Long::longValue).average().orElse(0);

        double semanaMaisRecente = volumesPorSemana.get(0);
        double semanaAnterior = volumesPorSemana.get(1);
        double duasSemanas = volumesPorSemana.get(2);

        String tendencia;
        if (semanaMaisRecente > semanaAnterior && semanaAnterior > duasSemanas) {
            tendencia = "CRESCENTE";
        } else if (semanaMaisRecente < semanaAnterior && semanaAnterior < duasSemanas) {
            tendencia = "DECRESCENTE";
        } else if (Math.abs(semanaMaisRecente - semanaAnterior) > 10 ||
                Math.abs(semanaAnterior - duasSemanas) > 10) {
            tendencia = "VARIÁVEL";
        } else {
            tendencia = "ESTÁVEL";
        }

        resultado.put("volumeMedioKm", Math.round(volumeMedio * 10.0) / 10.0);
        resultado.put("volumeMinimoKm", Math.round(volumeMinimo * 10.0) / 10.0);
        resultado.put("volumeMaximoKm", Math.round(volumeMaximo * 10.0) / 10.0);
        resultado.put("tendencia", tendencia);
        resultado.put("tssMedioPorSemana", tssMedioSemanal);
        resultado.put("treinosPorSemana", Math.round(treinosMedioSemanal * 10.0) / 10.0);
        resultado.put("volumeSemanaMaisRecente", Math.round(semanaMaisRecente * 10.0) / 10.0);
        resultado.put("volumeSemanaAnterior", Math.round(semanaAnterior * 10.0) / 10.0);
        resultado.put("volumeDuasSemanas", Math.round(duasSemanas * 10.0) / 10.0);

        return resultado;
    }
}
