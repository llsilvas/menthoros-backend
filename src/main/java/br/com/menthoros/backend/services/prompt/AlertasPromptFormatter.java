package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.MetricasThresholds;
import br.com.menthoros.backend.enums.NivelAlerta;
import br.com.menthoros.backend.services.impl.MetricasAlertaService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Formatador de alertas e hierarquia de decisão para prompts de IA.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Geração de alertas baseados em métricas (TSB, Ramp Rate, dias consecutivos)</li>
 *   <li>Alertas obrigatórios consolidados (topo do prompt)</li>
 *   <li>Alertas de padrão de dias</li>
 *   <li>Hierarquia de decisão para resolução de conflitos</li>
 *   <li>Restrições e histórico de lesões</li>
 * </ul>
 */
@Component
public class AlertasPromptFormatter {

    private final MetricasAlertaService metricasAlertaService;

    public AlertasPromptFormatter(MetricasAlertaService metricasAlertaService) {
        this.metricasAlertaService = metricasAlertaService;
    }

    /**
     * Gera alertas formatados baseados na análise centralizada de métricas.
     */
    public String gerarAlertas(PlanoMetaDados metaDados) {
        if (metaDados == null) {
            return "⚠️ Metadados não disponíveis. Inicialize o perfil do atleta.";
        }

        var analise = metricasAlertaService.analisarMetricas(metaDados);
        var alertasAtivos = analise.alertas();

        if (alertasAtivos.isEmpty()) {
            if (metaDados.getTsbAtual() == null && metaDados.getCtlAtual() == null) {
                return """
                        📊 **Iniciando coleta de dados:**
                        ℹ️ TSB e CTL não calculados ainda - necessário histórico de treinos

                        💡 Após alguns treinos, o sistema gerará recomendações personalizadas baseadas em suas métricas.
                        """;
            }
            return "✅ Nenhum alerta. Atleta em condições ideais para progredir.";
        }

        var criticos = alertasAtivos.stream().filter(a -> a.nivel() == NivelAlerta.CRITICO).toList();
        var altos = alertasAtivos.stream().filter(a -> a.nivel() == NivelAlerta.ALTO).toList();
        var atencao = alertasAtivos.stream().filter(a -> a.nivel() == NivelAlerta.ATENCAO).toList();
        var info = alertasAtivos.stream().filter(a -> a.nivel() == NivelAlerta.INFO).toList();

        StringBuilder resultado = new StringBuilder();

        if (!criticos.isEmpty()) {
            resultado.append("🔴 **ALERTAS CRÍTICOS:**\n");
            criticos.forEach(a ->
                    resultado.append(String.format("- %s\n  **AÇÃO OBRIGATÓRIA:** %s\n\n",
                            a.mensagem(), a.recomendacao()))
            );
        }

        if (!altos.isEmpty()) {
            resultado.append("⚠️ **ALERTAS IMPORTANTES:**\n");
            altos.forEach(a ->
                    resultado.append(String.format("- %s\n  **RECOMENDAÇÃO:** %s\n\n",
                            a.mensagem(), a.recomendacao()))
            );
        }

        if (!atencao.isEmpty()) {
            resultado.append("🟡 **PONTOS DE ATENÇÃO:**\n");
            atencao.forEach(a ->
                    resultado.append(String.format("- %s\n  **SUGESTÃO:** %s\n\n",
                            a.mensagem(), a.recomendacao()))
            );
        }

        if (!info.isEmpty()) {
            info.forEach(a ->
                    resultado.append(String.format("✅ %s\n  %s\n\n",
                            a.mensagem(), a.recomendacao()))
            );
        }

        return resultado.toString().trim();
    }

    /**
     * Gera alertas obrigatórios consolidados para o topo do prompt.
     *
     * <p>Delega a análise de métricas (TSB, Ramp Rate, dias consecutivos, progressão)
     * ao {@link MetricasAlertaService} com thresholds adaptados ao nível do atleta.
     * Adiciona apenas alertas que dependem de dados não agregados:
     * lesões ativas (Atleta), ausência de estímulos e RPE (TreinoRealizado).
     *
     * @param maxDiasConsecutivos limite configurado de disponibilidade; usado como aviso
     *                            de borda quando MetricasAlertaService ainda não disparou
     */
    public String gerarAlertasObrigatorios(Atleta atleta, PlanoMetaDados metaDados,
                                            int maxDiasConsecutivos,
                                            List<TreinoRealizado> treinosUltimas4Semanas,
                                            LocalDate dataReferencia) {
        List<String> alertas = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        if (atleta == null) {
            return "⚠️ Atleta não informado. Não foi possível gerar alertas obrigatórios.";
        }
        if (dataReferencia == null) {
            dataReferencia = LocalDate.now();
        }
        if (treinosUltimas4Semanas == null) {
            treinosUltimas4Semanas = List.of();
        }

        // 1. Alertas de métricas — delega ao MetricasAlertaService (sem duplicação de lógica)
        boolean diasConsecutivosJaFlagrado = false;
        if (metaDados != null) {
            var analise = metricasAlertaService.analisarMetricas(metaDados, atleta.getNivelExperiencia());
            diasConsecutivosJaFlagrado = analise.alertaDiasConsecutivos();

            for (var alerta : analise.alertas()) {
                if (alerta.nivel() == NivelAlerta.INFO) continue;
                String emoji = (alerta.nivel() == NivelAlerta.ATENCAO) ? "🟡" : "🔴";
                alertas.add(String.format("%s %s → %s", emoji, alerta.mensagem(), alerta.recomendacao()));
            }

            // Aviso de borda: maxDiasConsecutivos pode ser menor que o threshold do serviço
            if (!diasConsecutivosJaFlagrado) {
                Integer diasConsecutivos = metaDados.getDiasConsecutivosTreino();
                if (diasConsecutivos != null && diasConsecutivos >= maxDiasConsecutivos) {
                    alertas.add("🟡 NO LIMITE: " + diasConsecutivos
                            + " dias consecutivos → Próximo treino DEVE ser descanso");
                }
            }
        }

        // 2. Lesão ativa — exclusivo do Atleta, fora do escopo do MetricasAlertaService
        if (atleta.getTemLesao() != null && atleta.getTemLesao()) {
            alertas.add("🔴 LESÃO ATIVA: "
                    + (atleta.getDescricaoLesao() != null ? atleta.getDescricaoLesao() : "Lesão reportada")
                    + " → Máximo Z2, sem intervalados");
        }

        // 3. Estímulos ausentes — exclusivo da lista de TreinoRealizado
        Map<String, LocalDate> ultimaDataPorTipo = extrairUltimaDataPorTipo(treinosUltimas4Semanas);
        List<String> tiposMonitorados = Arrays.asList("FARTLEK", "TEMPO_RUN", "INTERVALADO");

        for (String tipo : tiposMonitorados) {
            if (!ultimaDataPorTipo.containsKey(tipo)) {
                alertas.add("🟡 " + tipo + ": NUNCA realizado → CONSIDERAR INCLUSÃO");
            } else {
                long diasDesde = ChronoUnit.DAYS.between(ultimaDataPorTipo.get(tipo), dataReferencia);
                if (diasDesde > 21) {
                    alertas.add("🔴 " + tipo + ": ausente há " + diasDesde + " dias → REINTRODUZIR ESTA SEMANA");
                }
            }
        }

        // 4. RPE médio alto — exclusivo da lista de TreinoRealizado
        List<TreinoRealizado> treinosComRpe = treinosUltimas4Semanas.stream()
                .filter(t -> t.getPercepcaoEsforco() != null)
                .collect(Collectors.toList());

        double rpeMedia = treinosComRpe.stream()
                .mapToInt(TreinoRealizado::getPercepcaoEsforco)
                .average()
                .orElse(0);

        if (treinosComRpe.size() >= 3) {
            long treinosRpeAlto = treinosComRpe.stream()
                    .filter(t -> t.getPercepcaoEsforco() >= 8)
                    .count();
            double percentualRpeAlto = (double) treinosRpeAlto / treinosComRpe.size() * 100;

            if (percentualRpeAlto > 50) {
                alertas.add("🔴 FADIGA ALTA: " + String.format("%.0f", percentualRpeAlto)
                        + "% dos treinos com RPE ≥8 → REDUZIR INTENSIDADE ESTA SEMANA");
            } else if (rpeMedia >= 7.5) {
                alertas.add("🟡 RPE MÉDIO ALTO: " + String.format("%.1f", rpeMedia)
                        + "/10 → REDUZIR INTENSIDADE ESTA SEMANA");
            }
        } else if (rpeMedia >= 7.5 && !treinosComRpe.isEmpty()) {
            alertas.add("🟡 RPE MÉDIO ALTO: " + String.format("%.1f", rpeMedia)
                    + "/10 → REDUZIR INTENSIDADE ESTA SEMANA");
        }

        if (alertas.isEmpty()) {
            return "✅ **NENHUM ALERTA CRÍTICO** - Atleta em condições normais de treino\n\n";
        }

        sb.append("## ⛔ ALERTAS OBRIGATÓRIOS (PROCESSE PRIMEIRO)\n\n");
        for (int i = 0; i < alertas.size(); i++) {
            sb.append(String.format("%d. %s\n", i + 1, alertas.get(i)));
        }
        sb.append("\n**HIERARQUIA:** Alertas 🔴 têm prioridade sobre 🟡. Segurança primeiro!\n\n");

        return sb.toString();
    }

    /**
     * Gera alertas sobre padrão de dias consecutivos e progressão.
     */
    public String gerarAlertasDias(PlanoMetaDados metaDados) {
        List<String> alertas = new ArrayList<>();

        Integer diasConsecutivos = metaDados.getDiasConsecutivosTreino();
        Integer diasDesdeDescanso = metaDados.getDiasDesdeUltimoDescanso();

        if (diasConsecutivos != null && diasConsecutivos > MetricasThresholds.DIAS_CONSECUTIVOS_CRITICO) {
            alertas.add("🔴 CRÍTICO: " + diasConsecutivos + "+ dias consecutivos sem descanso! DESCANSO IMEDIATO OBRIGATÓRIO!");
        } else if (diasConsecutivos != null && diasConsecutivos >= MetricasThresholds.DIAS_CONSECUTIVOS_CRITICO) {
            alertas.add("🟠 ALERTA: " + diasConsecutivos + " dias consecutivos. Dia de descanso urgente!");
        } else if (diasConsecutivos != null && diasConsecutivos >= MetricasThresholds.DIAS_CONSECUTIVOS_ALTO) {
            alertas.add("🟡 ATENÇÃO: " + diasConsecutivos + " dias consecutivos. Considerar descanso em breve.");
        }

        if (diasDesdeDescanso != null && diasDesdeDescanso >= 10) {
            alertas.add("🔴 CRÍTICO: 10+ dias desde último descanso completo!");
        } else if (diasDesdeDescanso != null && diasDesdeDescanso >= 7) {
            alertas.add("🟠 ALERTA: 7+ dias desde último descanso.");
        }

        Integer semanas = metaDados.getSemanasProgressaoContinua();
        if (semanas != null && semanas >= MetricasThresholds.SEMANAS_PROGRESSAO_ALERTA) {
            alertas.add("🟡 " + semanas + "+ semanas de progressão contínua. Considerar semana regenerativa.");
        }

        if (alertas.isEmpty()) {
            return "";
        }

        return "\n### ⚠️ Alertas de Padrão de Dias\n" + String.join("\n", alertas) + "\n";
    }

    /**
     * Gera seção de hierarquia de decisão para resolução de conflitos no prompt.
     */
    public String gerarHierarquiaDecisao(PlanoMetaDados metaDados, Atleta atleta) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🎯 HIERARQUIA DE DECISÃO (resolver conflitos nesta ordem)\n\n");

        sb.append("**NÍVEL 1 - SEGURANÇA (sempre vence):**\n");
        sb.append("- Se RPE médio > 7.5 → FORÇAR semana leve\n");
        sb.append("- Se TSB < -25 → APENAS Z1-Z2, máximo 3 treinos\n");
        sb.append("- Se lesão ativa → máximo Z2, sem intervalados\n");
        sb.append("- Se dias consecutivos >= limite → DESCANSO OBRIGATÓRIO\n\n");

        sb.append("**NÍVEL 2 - RECUPERAÇÃO:**\n");
        Double tsb = metaDados != null ? metaDados.getTsbAtual() : null;
        if (tsb != null && tsb < -15) {
            sb.append("- TSB " + String.format("%.1f", tsb) + " → REDUZIR volume 30-50%\n");
        }
        Integer semanas = metaDados != null ? metaDados.getSemanasProgressaoContinua() : null;
        if (semanas != null && semanas >= 4) {
            sb.append("- " + semanas + " semanas progressão → semana regenerativa (-40-50% volume)\n");
        }
        sb.append("- Se recomendação = regenerativa → REDUZIR volume 40-50%\n\n");

        sb.append("**NÍVEL 3 - VARIABILIDADE:**\n");
        sb.append("- Incluir estímulos ausentes há >14 dias\n");
        sb.append("- Alternar categorias de intervalado (não repetir 2 semanas seguidas)\n");
        sb.append("- Se conflitar com N1/N2 → usar versão LEVE do estímulo\n\n");

        sb.append("**NÍVEL 4 - OBJETIVO:**\n");
        sb.append("- Alinhar treino-chave com meta do atleta\n");
        sb.append("- Respeitar fase de periodização (BASE/BUILD/ESPECÍFICO/TAPER)\n\n");

        return sb.toString();
    }

    /**
     * Formata restrições e histórico de lesões do atleta.
     */
    public String formatarRestricoesLesoes(Atleta atleta, LocalDate dataReferencia) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🚨 RESTRIÇÕES E HISTÓRICO DE SAÚDE\n\n");

        if (atleta.getTemLesao() != null && atleta.getTemLesao()) {
            sb.append("**LESÃO ATIVA:**\n");
            if (atleta.getDescricaoLesao() != null) {
                sb.append("- Descrição: ").append(atleta.getDescricaoLesao()).append("\n");
            }
            if (atleta.getDataUltimaLesao() != null) {
                long diasDesdeLesao = ChronoUnit.DAYS.between(atleta.getDataUltimaLesao(), dataReferencia);
                sb.append("- Dias desde lesão: ").append(diasDesdeLesao).append(" dias\n");
            }
            sb.append("- **REGRAS:** Máximo Z2, sem intervalados, sem longos >60min\n\n");
        }

        if (atleta.getHistoricoLesoes() != null && !atleta.getHistoricoLesoes().trim().isEmpty()) {
            sb.append("**HISTÓRICO DE LESÕES:**\n");
            sb.append(atleta.getHistoricoLesoes()).append("\n\n");

            String historicoLower = atleta.getHistoricoLesoes().toLowerCase();
            if (historicoLower.contains("canelite") || historicoLower.contains("shin splint")) {
                sb.append("- ⚠️ Histórico de canelite → evitar superfícies duras, limitar volume subitamente\n");
            }
            if (historicoLower.contains("fascite") || historicoLower.contains("plantar")) {
                sb.append("- ⚠️ Histórico de fascite plantar → aquecimento estendido, evitar sprints\n");
            }
            if (historicoLower.contains("tendinite") || historicoLower.contains("tendão")) {
                sb.append("- ⚠️ Histórico de tendinite → progressão muito gradual, fortalecimento complementar\n");
            }
            sb.append("\n");
        }

        if ((atleta.getTemLesao() == null || !atleta.getTemLesao()) &&
                (atleta.getHistoricoLesoes() == null || atleta.getHistoricoLesoes().trim().isEmpty())) {
            sb.append("✅ Nenhuma lesão ativa ou histórico de lesões reportado\n\n");
        }

        return sb.toString();
    }

    /**
     * Extrai a última data de cada tipo de treino a partir de uma lista de treinos.
     */
    public Map<String, LocalDate> extrairUltimaDataPorTipo(List<TreinoRealizado> treinos) {
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
}
