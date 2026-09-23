package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FatigueSignalType;
import br.com.menthoros.backend.services.helper.FatigueSignal;
import br.com.menthoros.backend.services.helper.WeeklyCoverageContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bloco "COBERTURA DA SEMANA" do prompt: diz à LLM que todo dia disponível recebe treino ou
 * descanso, quais sinais estão ativos (com valor e limiar) e o que cada um autoriza
 * (add-descanso-explicito-por-fadiga).
 *
 * <p>Escrito a partir do <b>mesmo</b> {@link WeeklyCoverageContext} que o validador usa — é o que
 * evita o prompt pedir uma coisa e a validação cobrar outra.</p>
 */
@Component
public class CoberturaSemanalPromptFormatter {

    public String formatar(@Nullable WeeklyCoverageContext ctx) {
        if (ctx == null || ctx.effectiveDays().isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("## 📆 COBERTURA DA SEMANA (obrigatório)\n\n");
        sb.append("**Dias a cobrir:** ").append(nomes(ctx.effectiveDays())).append("\n");
        sb.append("Cada um desses dias recebe **um treino** ou **um descanso declarado** em `restDays`. ")
                .append("Não deixe nenhum dia de fora e não repita dia.\n")
                .append("`restDays` é só para dias **desta lista** que deixam de ter treino. Os outros dias da ")
                .append("semana já são folga do atleta: não os declare em `restDays` nem em `treinosPlanejados`.\n\n");

        List<FatigueSignal> sinais = ctx.sinaisEfetivos();
        if (sinais.isEmpty()) {
            sb.append("**Sinais de fadiga:** nenhum. Todos os dias recebem treino; `restDays` fica vazio.\n");
            return sb.toString();
        }

        sb.append("**Sinais de fadiga detectados:**\n");
        for (FatigueSignal sinal : sinais) {
            sb.append("- ").append(descrever(sinal)).append(" → ").append(efeito(sinal)).append("\n");
        }

        Set<DiaSemana> permitidos = ctx.diasComDescansoPermitido();
        sb.append("\n");
        if (permitidos.isEmpty()) {
            sb.append("**Descanso:** não autorizado nesta semana. Onde a intensidade não couber, ")
                    .append("prescreva treino leve (REGENERATIVO ou CONTINUO em Z1-Z2) — nunca omita o dia.\n");
        } else {
            sb.append("**Descanso:** no máximo ").append(ctx.limiteDescansos())
                    .append(", e apenas em: ").append(nomes(List.copyOf(permitidos)))
                    .append(". Cada descanso vai em `restDays` com `reason` citando o sinal, o valor e o limiar.\n")
                    .append("Nos demais dias, se a intensidade não couber, prescreva treino leve — nunca omita o dia.\n");
        }
        return sb.toString();
    }

    /** "TSB -18,0 (limiar -15,0)" — o treinador precisa do número e da régua para confiar. */
    private String descrever(FatigueSignal sinal) {
        String nome = switch (sinal.type()) {
            case TSB_BAIXO -> "TSB";
            case RPE_ALTO -> "RPE médio 7d";
            case RECUPERACAO_INSUFICIENTE -> "horas desde o último treino intenso";
            case DIAS_CONSECUTIVOS_LIMITE -> "dias consecutivos treinando";
            case READINESS_DESCANSAR -> "check-in de prontidão de hoje";
            case SEQUENCIA_ACIMA_DO_MAXIMO -> "dias seguidos disponíveis";
            case CTL_BAIXO -> "CTL";
        };
        if (sinal.type() == FatigueSignalType.READINESS_DESCANSAR) {
            return nome + ": DESCANSAR";
        }
        if (sinal.value() == null || sinal.threshold() == null) {
            return nome;
        }
        return String.format("%s %.1f (limiar %.1f)", nome, sinal.value(), sinal.threshold());
    }

    private String efeito(FatigueSignal sinal) {
        return sinal.liberaDescanso()
                ? "pode virar descanso, nos dias listados abaixo"
                : "reduza a intensidade do dia (treino leve), sem tirar a sessão";
    }

    private String nomes(List<DiaSemana> dias) {
        return dias.stream().map(DiaSemana::name).collect(Collectors.joining(", "));
    }
}
