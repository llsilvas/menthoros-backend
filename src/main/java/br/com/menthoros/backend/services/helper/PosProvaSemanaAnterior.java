package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.DiaSemana;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Prova no dia anterior ao início da semana do plano: o primeiro dia do plano é o dia seguinte a
 * ela. O plano é segunda a domingo, então uma prova no domingo tem o D+1 na segunda do plano
 * seguinte, que não a enxerga (fix-planner-ignora-prova-na-semana, Decisão 4b).
 *
 * <p>{@code nomeProva} é texto livre do atleta e vai parar numa instrução do prompt e na mensagem
 * de reparo devolvida ao LLM — por isso é normalizado aqui, uma vez, por {@link #literalDePrompt}.
 *
 * @param diaDoPlano dia do plano que segue a prova (o {@code DiaSemana} de {@code inicioSemana})
 * @param nomeProva  nome cadastrado da prova, já seguro para interpolar no prompt
 * @param data       data da prova ({@code inicioSemana − 1})
 */
public record PosProvaSemanaAnterior(DiaSemana diaDoPlano, String nomeProva, LocalDate data) {

    private static final DateTimeFormatter DATA_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int NOME_MAX = 80;

    public PosProvaSemanaAnterior {
        nomeProva = literalDePrompt(nomeProva);
    }

    public String dataFormatada() {
        return data.format(DATA_FMT);
    }

    /**
     * Neutraliza injeção de prompt via texto livre interpolado numa instrução: tira caracteres de
     * controle (quebras de linha, tab) e separadores de linha Unicode, aspas que fechariam o literal,
     * colapsa espaços e limita o tamanho — um nome não precisa de mais que isso para o LLM.
     */
    public static String literalDePrompt(String texto) {
        if (texto == null) {
            return "";
        }
        String limpo = texto
                .replaceAll("[\\p{Cntrl}\\u2028\\u2029\"]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return limpo.length() > NOME_MAX ? limpo.substring(0, NOME_MAX).trim() : limpo;
    }
}
