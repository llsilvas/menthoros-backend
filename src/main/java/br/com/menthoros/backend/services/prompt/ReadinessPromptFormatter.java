package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.enums.NivelProntidao;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Formata a seção de readiness (prontidão subjetiva diária) para o prompt de geração de plano.
 * Recebe dados pré-carregados via {@code TreinoHistoricoProvider.ContextoTreino} — sem acesso
 * a repository.
 */
@Component
public class ReadinessPromptFormatter {

    /**
     * Gera a seção "readiness" do contexto: sequência dos últimos 7 dias + score/nível do dia
     * atual, com instrução mandatória quando o dia estiver classificado como DESCANSAR
     * (instruction hardening contra o LLM ignorar readiness baixo).
     */
    public String formatarReadiness(List<@Nullable NivelProntidao> sequenciaUltimos7Dias,
                                     @Nullable NivelProntidao nivelHoje,
                                     @Nullable BigDecimal scoreHoje) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🌡️ READINESS (PRONTIDÃO SUBJETIVA DIÁRIA)\n\n");

        String sequenciaFormatada = sequenciaUltimos7Dias.stream()
                .map(nivel -> nivel != null ? nivel.name() : "SEM_DADO")
                .collect(Collectors.joining(", "));
        sb.append("- **Últimos 7 dias:** [").append(sequenciaFormatada).append("]\n");

        if (nivelHoje == null) {
            sb.append("- **Hoje:** sem checkin registrado — decisão de intervalado não considera readiness.\n");
            return sb.toString();
        }

        sb.append(String.format("- **Hoje:** %s (score %.2f) — %s\n",
                nivelHoje.name(),
                scoreHoje != null ? scoreHoje.doubleValue() : 0.0,
                nivelHoje.getInterpretacao()));

        if (nivelHoje == NivelProntidao.DESCANSAR) {
            sb.append("\n**⚠️ ATENÇÃO OBRIGATÓRIA:** readiness do dia é DESCANSAR. NÃO prescreva ")
              .append("intervalado nem intensidade alta hoje, independentemente de outros sinais. ")
              .append("Priorize recuperação (REGENERATIVO/CONTINUO leve).\n");
        }

        return sb.toString();
    }
}
