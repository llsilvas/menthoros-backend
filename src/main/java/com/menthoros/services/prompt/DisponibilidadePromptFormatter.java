package com.menthoros.services.prompt;

import com.menthoros.entity.Atleta;
import com.menthoros.entity.PlanoMetaDados;
import com.menthoros.enums.DiaSemana;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Formatador de disponibilidade e padrões de dias para prompts de IA.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Formatação de dias disponíveis e padrões de treino</li>
 *   <li>Cálculo de máximo de dias consecutivos seguros</li>
 *   <li>Recomendação de dia de descanso</li>
 *   <li>Sugestão de distribuição semanal</li>
 *   <li>Alertas de padrões de dias</li>
 * </ul>
 */
@Component
public class DisponibilidadePromptFormatter {

    private static final DateTimeFormatter DATA_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Formata bloco completo de disponibilidade e padrões de treino para o prompt.
     */
    public String formatarDisponibilidade(Atleta atleta, PlanoMetaDados metaDados, LocalDate inicioSemana) {
        if (atleta == null) {
            return "⚠️ Atleta não informado. Não foi possível formatar disponibilidade.";
        }
        if (metaDados == null) {
            return "⚠️ Metadados não informados. Não foi possível formatar disponibilidade.";
        }

        StringBuilder sb = new StringBuilder();

        sb.append("## 📅 PADRÕES DE TREINO E DISPONIBILIDADE\n\n");

        if (inicioSemana != null) {
            sb.append(String.format("**Semana referência:** %s a %s\n\n",
                    inicioSemana.format(DATA_FMT),
                    inicioSemana.plusDays(6).format(DATA_FMT)));
        }

        // Dias disponíveis do atleta
        sb.append("### Disponibilidade Semanal\n");
        if (atleta.getDiasDisponiveis() != null && !atleta.getDiasDisponiveis().isEmpty()) {
            sb.append("**Dias disponíveis para treinar:** ");
            sb.append(atleta.getDiasDisponiveis().stream()
                    .map(DiaSemana::getLabel)
                    .collect(Collectors.joining(", ")));
            sb.append("\n");
            sb.append(String.format("**Total:** %d dias/semana\n\n", atleta.getDiasDisponiveis().size()));
        } else {
            sb.append("⚠️ **Dias disponíveis não cadastrados** - Assumir disponibilidade total\n\n");
        }

        // Dia preferido para treino longo
        if (metaDados.getDiaPreferidoLongo() != null) {
            sb.append(String.format("**Dia preferido para treino longo:** %s\n\n",
                    metaDados.getDiaPreferidoLongo().getLabel()));
        }

        // Padrões atuais
        sb.append("### Padrão Atual de Treino\n");
        sb.append(String.format("- **Dias consecutivos treinando:** %d dias\n",
                metaDados.getDiasConsecutivosTreino() != null ? metaDados.getDiasConsecutivosTreino() : 0));
        sb.append(String.format("- **Dias desde último descanso:** %d dias\n",
                metaDados.getDiasDesdeUltimoDescanso() != null ? metaDados.getDiasDesdeUltimoDescanso() : 0));
        sb.append(String.format("- **Semanas de progressão contínua:** %d semanas\n\n",
                metaDados.getSemanasProgressaoContinua() != null ? metaDados.getSemanasProgressaoContinua() : 0));

        // Máximo de dias consecutivos recomendado
        int maxDiasConsecutivos = calcularMaxDiasConsecutivos(metaDados, atleta);
        sb.append("### Recomendações para Esta Semana\n");
        sb.append(String.format("- **Máximo de dias consecutivos recomendado:** %d dias\n", maxDiasConsecutivos));

        // Alerta se já está no limite
        if (metaDados.getDiasConsecutivosTreino() != null &&
                metaDados.getDiasConsecutivosTreino() >= maxDiasConsecutivos) {
            sb.append("- ⚠️ **ALERTA:** Atleta já atingiu ou ultrapassou o limite recomendado\n");
            sb.append("- **AÇÃO:** Incluir dia de descanso completo ou regenerativo OBRIGATÓRIO\n");
        }

        // Dia de descanso recomendado
        String diaDescansoRecomendado = recomendarDiaDescanso(metaDados, atleta);
        if (diaDescansoRecomendado != null) {
            sb.append(String.format("- **Dia de descanso sugerido:** %s\n", diaDescansoRecomendado));
        }

        sb.append("\n");

        // Volume médio por dia
        if (metaDados.getVolumeSemanalMedio() != null && metaDados.getTreinosPorSemanaMedio() > 0) {
            double volumeMedioPorTreino = metaDados.getVolumeSemanalMedio().doubleValue() /
                    metaDados.getTreinosPorSemanaMedio();
            sb.append(String.format("**Volume médio por treino:** %.1f km\n", volumeMedioPorTreino));
        }

        if (metaDados.getTssSemanalMedio() != null && metaDados.getTreinosPorSemanaMedio() > 0) {
            double tssMedioPorTreino = metaDados.getTssSemanalMedio() /
                    metaDados.getTreinosPorSemanaMedio();
            sb.append(String.format("**TSS médio por treino:** %.0f pontos\n", tssMedioPorTreino));
        }

        sb.append("\n---\n");

        return sb.toString();
    }

    /**
     * Calcula o máximo de dias consecutivos que o atleta pode treinar com segurança.
     * Baseado em TSB, fadiga, experiência, ramp rate, CTL e histórico de lesões.
     */
    public int calcularMaxDiasConsecutivos(PlanoMetaDados metaDados, Atleta atleta) {
        if (metaDados == null || atleta == null) {
            return 5;
        }

        int maxDias = 5; // Valor padrão conservador

        // Fator 1: TSB (Prontidão/Fadiga)
        Double tsb = metaDados.getTsbAtual();
        if (tsb != null) {
            if (tsb < -30) {
                maxDias = 2;
            } else if (tsb < -20) {
                maxDias = 3;
            } else if (tsb < -10) {
                maxDias = 4;
            } else if (tsb >= -10 && tsb < 5) {
                maxDias = 5;
            } else {
                maxDias = 6;
            }
        }

        // Fator 2: Nível do atleta (experiência)
        if (atleta.getNivelExperiencia() != null) {
            switch (atleta.getNivelExperiencia()) {
                case INICIANTE:
                    maxDias = Math.min(maxDias, 4);
                    break;
                case INTERMEDIARIO:
                    maxDias = Math.min(maxDias, 5);
                    break;
                case AVANCADO:
                    break;
                case ELITE:
                    maxDias = Math.min(maxDias + 1, 7);
                    break;
            }
        }

        // Fator 3: Ramp Rate (taxa de progressão)
        Double rampRate = metaDados.getRampRateAtual();
        if (rampRate != null && rampRate > 8) {
            maxDias = Math.max(maxDias - 1, 3);
        }

        // Fator 4: Dias já consecutivos
        Integer diasConsecutivos = metaDados.getDiasConsecutivosTreino();
        if (diasConsecutivos != null && diasConsecutivos >= 5) {
            maxDias = Math.min(maxDias, 3);
        }

        // Fator 5: CTL (Fitness de base)
        Double ctl = metaDados.getCtlAtual();
        if (ctl != null && ctl > 80) {
            maxDias = Math.min(maxDias + 1, 7);
        } else if (ctl != null && ctl < 30) {
            maxDias = Math.max(maxDias - 1, 3);
        }

        // Fator 6: Histórico de lesões
        if (atleta.getHistoricoLesoes() != null && !atleta.getHistoricoLesoes().isEmpty()) {
            maxDias = Math.max(maxDias - 1, 3);
        }

        // Garantir limites razoáveis
        return Math.max(3, Math.min(maxDias, 7));
    }

    /**
     * Versão simplificada quando chamada apenas com Atleta (sem métricas).
     */
    public int calcularMaxDiasConsecutivos(Atleta atleta) {
        if (atleta.getNivelExperiencia() == null) {
            return 5;
        }

        return switch (atleta.getNivelExperiencia()) {
            case INICIANTE -> 4;
            case INTERMEDIARIO -> 5;
            case AVANCADO -> 6;
            case ELITE -> 7;
        };
    }

    /**
     * Retorna distribuição ideal de treinos na semana.
     */
    public String sugerirDistribuicaoSemanal(PlanoMetaDados metaDados, Atleta atleta) {
        int maxDiasConsecutivos = calcularMaxDiasConsecutivos(metaDados, atleta);
        List<DiaSemana> diasDisponiveis = atleta.getDiasDisponiveis();

        if (diasDisponiveis == null || diasDisponiveis.isEmpty()) {
            return "Distribuição sugerida não disponível - cadastrar dias disponíveis do atleta";
        }

        int totalDias = diasDisponiveis.size();

        if (totalDias <= 3) {
            return String.format("%d treinos distribuídos nos dias disponíveis (sem dias consecutivos necessários)", totalDias);
        } else if (totalDias == 4) {
            return "Sugestão: 2-1-1 (2 dias, descanso, 1 dia, descanso, 1 dia)";
        } else if (totalDias == 5) {
            return String.format("Sugestão: 2-1-2 ou 3-1-1 (máx %d dias consecutivos)", maxDiasConsecutivos);
        } else if (totalDias == 6) {
            return String.format("Sugestão: 3-1-2 ou 2-1-3 (máx %d dias consecutivos, 1 descanso obrigatório)", maxDiasConsecutivos);
        } else {
            return String.format("Treino diário disponível - incluir pelo menos 1 dia de descanso (máx %d consecutivos)", maxDiasConsecutivos);
        }
    }

    /**
     * Gera alertas específicos sobre padrões de dias.
     */
    public String gerarAlertasDias(PlanoMetaDados metaDados) {
        List<String> alertas = new ArrayList<>();

        Integer diasConsecutivos = metaDados.getDiasConsecutivosTreino();
        Integer diasDesdeDescanso = metaDados.getDiasDesdeUltimoDescanso();

        if (diasConsecutivos != null && diasConsecutivos >= 7) {
            alertas.add("🔴 CRÍTICO: 7+ dias consecutivos sem descanso! DESCANSO IMEDIATO OBRIGATÓRIO!");
        } else if (diasConsecutivos != null && diasConsecutivos >= 6) {
            alertas.add("🟠 ALERTA: 6 dias consecutivos. Dia de descanso urgente!");
        } else if (diasConsecutivos != null && diasConsecutivos >= 5) {
            alertas.add("🟡 ATENÇÃO: 5 dias consecutivos. Considerar descanso em breve.");
        }

        if (diasDesdeDescanso != null && diasDesdeDescanso >= 10) {
            alertas.add("🔴 CRÍTICO: 10+ dias desde último descanso completo!");
        } else if (diasDesdeDescanso != null && diasDesdeDescanso >= 7) {
            alertas.add("🟠 ALERTA: 7+ dias desde último descanso.");
        }

        Integer semanas = metaDados.getSemanasProgressaoContinua();
        if (semanas != null && semanas >= 4) {
            alertas.add("🟡 4+ semanas de progressão contínua. Considerar semana regenerativa.");
        }

        if (alertas.isEmpty()) {
            return "";
        }

        return "\n### ⚠️ Alertas de Padrão de Dias\n" + String.join("\n", alertas) + "\n";
    }

    /**
     * Formata lista simples de dias disponíveis.
     */
    public String formatarDias(List<DiaSemana> diasDisponiveis) {
        if (diasDisponiveis == null || diasDisponiveis.isEmpty()) {
            return "Não informado (assumir 7 dias/semana)";
        }

        return diasDisponiveis.stream()
                .map(DiaSemana::getLabel)
                .collect(Collectors.joining(", "));
    }

    // --- Métodos privados ---

    private String recomendarDiaDescanso(PlanoMetaDados metaDados, Atleta atleta) {
        if (metaDados == null || atleta == null) {
            return null;
        }

        Integer diasConsecutivos = metaDados.getDiasConsecutivosTreino();
        Integer diasDesdeDescanso = metaDados.getDiasDesdeUltimoDescanso();

        // Se não precisa de descanso urgente, retornar null
        if ((diasConsecutivos == null || diasConsecutivos < 4) &&
                (diasDesdeDescanso == null || diasDesdeDescanso < 5)) {
            return null;
        }

        List<DiaSemana> diasDisponiveis = atleta.getDiasDisponiveis();
        if (diasDisponiveis == null || diasDisponiveis.isEmpty()) {
            return "Quarta-feira (meio da semana)";
        }

        // Se treina 6-7 dias, recomendar meio da semana
        if (diasDisponiveis.size() >= 6) {
            return "Quarta-feira ou Quinta-feira (quebrar a semana)";
        }

        // Se treina 5 dias, um dos dias que não treina já serve
        if (diasDisponiveis.size() == 5) {
            List<DiaSemana> diasNaoTreina = Arrays.stream(DiaSemana.values())
                    .filter(d -> !diasDisponiveis.contains(d))
                    .toList();

            if (!diasNaoTreina.isEmpty()) {
                return diasNaoTreina.get(0).getLabel() + " (já é dia de folga)";
            }
        }

        return "Quarta-feira (meio da semana)";
    }
}
