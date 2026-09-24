package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.ai.ledger.Violacao;
import br.com.menthoros.backend.dto.llm.RestDayLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.TipoTreino;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cobertura da semana: todo dia disponível recebe treino ou descanso, e descanso só existe com um
 * sinal que o libere (add-descanso-explicito-por-fadiga, Decisão 2).
 *
 * <p>Motivo: a LLM omitia dias em silêncio — 5 de 7 gerações do plano do Leandro em 22/09 vieram
 * sem a quinta, sempre o dia da sessão de intensidade, e um dia omitido é indistinguível de um
 * esquecimento. As violações voltam para o turno de reparo, com o dia e a saída válida na mensagem.</p>
 *
 * <p>Idempotent: YES — função pura sobre as listas recebidas. Side Effects: NONE.
 * Tenant-aware: NO.</p>
 */
@Component
public class WeeklyCoverageValidator {

    /** Tipos que exigem 36h+ de recuperação — dois deles em dias vizinhos é carga mal distribuída. */
    static final Set<TipoTreino> TIPOS_ALTA_INTENSIDADE = Set.of(
            TipoTreino.LONGO,
            TipoTreino.FARTLEK,
            TipoTreino.TEMPO_RUN,
            TipoTreino.PROVA,
            TipoTreino.INTERVALADO,
            TipoTreino.TIRO,
            TipoTreino.SUBIDA
    );

    private static final int MOTIVO_MAX = 200;

    /**
     * Dias de descanso que o plano declarou fora dos dias disponíveis do atleta — são descartados
     * antes de persistir (ver {@code registrarDias}).
     */
    public Set<DiaSemana> descansosForaDosDiasDisponiveis(@Nullable List<RestDayLlmDto> restDays,
                                                          WeeklyCoverageContext ctx) {
        if (restDays == null) return Set.of();
        return restDays.stream()
                .map(d -> converter(d.dayOfWeek()))
                .filter(d -> d != null && !ctx.effectiveDays().contains(d))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * @param treinos  treinos do plano (pode ser nulo)
     * @param restDays dias de descanso do plano (pode ser nulo)
     * @param ctx      dias a cobrir, sinais e limites
     * @return violações acumuladas — vazio quando o plano está conforme
     */
    public List<Violacao> validar(@Nullable List<TreinoPlanejadoLlmDto> treinos,
                                  @Nullable List<RestDayLlmDto> restDays,
                                  WeeklyCoverageContext ctx) {
        List<TreinoPlanejadoLlmDto> listaTreinos = treinos != null ? treinos : List.of();
        List<RestDayLlmDto> listaDescansos = restDays != null ? restDays : List.of();
        List<Violacao> violacoes = new ArrayList<>();

        Map<DiaSemana, Integer> ocupacao = new LinkedHashMap<>();
        Set<DiaSemana> descansosIgnorados = new LinkedHashSet<>();
        registrarDias(listaTreinos.stream().map(TreinoPlanejadoLlmDto::diaSemana).toList(),
                ctx, ocupacao, violacoes, "treino", descansosIgnorados);
        registrarDias(listaDescansos.stream().map(RestDayLlmDto::dayOfWeek).toList(),
                ctx, ocupacao, violacoes, "descanso", descansosIgnorados);

        ocupacao.forEach((dia, vezes) -> {
            if (vezes > 1) {
                violacoes.add(new Violacao("COBERTURA_DIAS",
                        "O dia " + dia.name() + " aparece mais de uma vez no plano (" + vezes
                                + "). Cada dia disponível recebe exatamente um treino ou um descanso."));
            }
        });

        List<String> naoCobertos = ctx.effectiveDays().stream()
                .filter(d -> !ocupacao.containsKey(d))
                .map(DiaSemana::name)
                .toList();
        if (!naoCobertos.isEmpty()) {
            violacoes.add(new Violacao("COBERTURA_DIAS",
                    "Dia(s) disponível(is) sem nada prescrito: " + String.join(", ", naoCobertos)
                            + ". Prescreva um treino em cada um — leve, se a intensidade não couber."));
        }

        List<RestDayLlmDto> descansosRelevantes = listaDescansos.stream()
                .filter(d -> {
                    DiaSemana dia = converter(d.dayOfWeek());
                    return dia == null || !descansosIgnorados.contains(dia);
                })
                .toList();
        violacoes.addAll(validarDescansos(descansosRelevantes, ctx));
        violacoes.addAll(validarIntensosAdjacentes(listaTreinos));
        return List.copyOf(violacoes);
    }

    /** Converte os dias declarados, acusando dia inválido ou fora dos disponíveis. */
    private void registrarDias(List<String> dias, WeeklyCoverageContext ctx,
                               Map<DiaSemana, Integer> ocupacao, List<Violacao> violacoes, String origem,
                               Set<DiaSemana> ignorados) {
        for (String bruto : dias) {
            DiaSemana dia = converter(bruto);
            if (dia == null) {
                violacoes.add(new Violacao("COBERTURA_DIAS",
                        "Dia de " + origem + " inválido: '" + bruto + "'. Use um dos dias disponíveis do atleta."));
                continue;
            }
            if (!ctx.effectiveDays().contains(dia)) {
                // Descanso fora dos dias disponíveis é ruído de vocabulário, não erro de prescrição: a
                // LLM lê "restDays" como "os dias de folga da semana" (geração real de 22/09 17:08,
                // que declarou quarta, sexta e domingo). Descartar é melhor que derrubar o plano —
                // o dia já não teria treino mesmo. Treino em dia indisponível continua violação.
                if ("descanso".equals(origem)) {
                    ignorados.add(dia);
                    continue;
                }
                violacoes.add(new Violacao("COBERTURA_DIAS",
                        "O " + origem + " de " + dia.name() + " não está disponível para este atleta nesta semana."));
                continue;
            }
            ocupacao.merge(dia, 1, Integer::sum);
        }
    }

    private List<Violacao> validarDescansos(List<RestDayLlmDto> descansos, WeeklyCoverageContext ctx) {
        List<Violacao> violacoes = new ArrayList<>();
        if (descansos.isEmpty()) return violacoes;

        if (descansos.size() > ctx.limiteDescansos()) {
            violacoes.add(new Violacao("DESCANSO_ACIMA_DO_LIMITE",
                    "O plano tem " + descansos.size() + " descansos; o máximo é " + ctx.limiteDescansos()
                            + " por semana. Os demais dias recebem treino leve."));
        }

        Set<DiaSemana> permitidos = ctx.diasComDescansoPermitido();
        for (RestDayLlmDto descanso : descansos) {
            DiaSemana dia = converter(descanso.dayOfWeek());
            if (dia != null && !permitidos.contains(dia)) {
                violacoes.add(new Violacao("DESCANSO_SEM_SINAL",
                        "Descanso em " + dia.name() + " não é permitido: " + motivoDaRecusa(ctx)
                                + " Prescreva treino leve nesse dia."));
            }
            String motivo = descanso.reason();
            if (motivo == null || motivo.isBlank() || motivo.length() > MOTIVO_MAX) {
                violacoes.add(new Violacao("DESCANSO_SEM_MOTIVO",
                        "Descanso em " + (dia != null ? dia.name() : descanso.dayOfWeek())
                                + " precisa de um motivo de até " + MOTIVO_MAX
                                + " caracteres, citando o sinal com valor e limiar."));
            }
        }
        return violacoes;
    }

    /** Explica por que aquele dia não pode ser descanso — é o que a LLM lê no turno de reparo. */
    private String motivoDaRecusa(WeeklyCoverageContext ctx) {
        Set<DiaSemana> permitidos = ctx.diasComDescansoPermitido();
        if (permitidos.isEmpty()) {
            return "não há sinal de fadiga do dia que o justifique (fadiga da semana, como TSB ou RPE, "
                    + "reduz a intensidade, não tira a sessão).";
        }
        return "com os sinais desta semana, só " + String.join(", ", permitidos.stream().map(DiaSemana::name).toList())
                + " pode(m) ser descanso.";
    }

    /** Dois treinos de alta intensidade em dias vizinhos — a regra que a redistribuição aplicava. */
    private List<Violacao> validarIntensosAdjacentes(List<TreinoPlanejadoLlmDto> treinos) {
        List<Violacao> violacoes = new ArrayList<>();
        List<Map.Entry<DiaSemana, String>> intensos = new ArrayList<>();
        Set<DiaSemana> vistos = new LinkedHashSet<>();

        for (TreinoPlanejadoLlmDto treino : treinos) {
            DiaSemana dia = converter(treino.diaSemana());
            if (dia == null || !vistos.add(dia)) continue;
            TipoTreino tipo = tipo(treino.tipoTreino());
            if (tipo != null && TIPOS_ALTA_INTENSIDADE.contains(tipo)) {
                intensos.add(Map.entry(dia, tipo.name()));
            }
        }
        intensos.sort(Comparator.comparingInt(e -> ordem(e.getKey())));

        for (int i = 1; i < intensos.size(); i++) {
            var anterior = intensos.get(i - 1);
            var atual = intensos.get(i);
            if (ordem(atual.getKey()) - ordem(anterior.getKey()) == 1) {
                violacoes.add(new Violacao("INTENSOS_ADJACENTES",
                        "Treinos de alta intensidade em dias vizinhos: " + anterior.getValue() + " em "
                                + anterior.getKey().name() + " e " + atual.getValue() + " em "
                                + atual.getKey().name() + ". Separe-os com um dia leve."));
            }
        }
        return violacoes;
    }

    private static @Nullable DiaSemana converter(@Nullable String dia) {
        if (dia == null || dia.isBlank()) return null;
        try {
            return DiaSemana.valueOf(dia.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static @Nullable TipoTreino tipo(@Nullable String tipoTreino) {
        if (tipoTreino == null || tipoTreino.isBlank()) return null;
        try {
            return TipoTreino.valueOf(tipoTreino.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int ordem(DiaSemana dia) {
        return WeeklyCoverageContext.ordemNaSemana(dia);
    }
}
