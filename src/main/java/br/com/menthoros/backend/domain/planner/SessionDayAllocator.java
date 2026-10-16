package br.com.menthoros.backend.domain.planner;

import br.com.menthoros.backend.enums.TipoTreino;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Atribui um dia a cada {@link SessionSlot} composto (design planner-engine-enforcement Decisao 4;
 * fix-planner-ignora-prova-na-semana Decisao 6; regras absorvidas da {@code WeeklyDistributionSkill}
 * orfa, agora em {@code domain/planner} — o registry de skills nao pode virar dependencia do
 * nucleo). Puro e deterministico.
 *
 * <p>Regras: (1) cada slot PROVA fica no dia real da prova correspondente (ancora fixa, ignora
 * disponibilidade — ordem de {@code composed} casada com a ordem de {@code diasDeProva}); (2) todo
 * LONGO (chave ou nao — TAPER compoe LONGO sem chave) vai para o dia preferido, senao o ultimo dia
 * disponivel, {@code nunca no D-1 de uma prova}, e o dia dele vira ancora de intensidade; (3) sessoes
 * duras nunca em dias consecutivos entre si, com o LONGO ou com uma prova (nem no D-1 de uma prova),
 * maximizando o espacamento — o legado ja trata LONGO como intenso em {@code INTENSOS_ADJACENTES}
 * (decisao 2026-10-16, tasks.md 7.2); (4) as demais (nunca LONGO/duras) preenchem os
 * dias livres em ordem canonica — podem cair no D-1, so sessao leve. LONGO/dura que nao coube nos
 * passos 2-3 fica com {@code day == null}: o chamador decide (remove do skeleton, marca para
 * revisao — nunca "(dia a definir)" no prompt).
 */
public final class SessionDayAllocator {

    static final EnumSet<TipoTreino> DURAS =
            EnumSet.of(TipoTreino.INTERVALADO, TipoTreino.TIRO, TipoTreino.TEMPO_RUN, TipoTreino.SUBIDA, TipoTreino.FARTLEK);

    /**
     * @param composed        slots com {@code day == null}, na ordem de prioridade (chave primeiro)
     * @param availableDays   dias disponiveis do atleta
     * @param preferredLongDay dia preferido do longao ({@code null} → usa o ultimo dia disponivel)
     * @param diasDeProva     dias reais das provas da semana, na mesma ordem dos slots PROVA em
     *                        {@code composed} (determinante, se houver, seguida das preparatorias)
     */
    public List<SessionSlot> allocate(List<SessionSlot> composed,
                                      List<DayOfWeek> availableDays,
                                      DayOfWeek preferredLongDay,
                                      List<DayOfWeek> diasDeProva) {
        if (composed == null) {
            throw new IllegalArgumentException("composed cannot be null");
        }
        if (composed.isEmpty()) {
            return List.of();
        }
        List<DayOfWeek> dias = availableDays == null ? List.of()
                : availableDays.stream().distinct().sorted(Comparator.comparingInt(DayOfWeek::getValue)).toList();
        List<DayOfWeek> provas = diasDeProva == null ? List.of() : diasDeProva;

        List<SessionSlot> result = new ArrayList<>(composed.size());
        for (int i = 0; i < composed.size(); i++) result.add(null);

        List<DayOfWeek> livres = new ArrayList<>(dias);
        List<DayOfWeek> diasComDura = new ArrayList<>(); // ancoras de intensidade (duras + provas)

        // 1. Cada slot PROVA no dia real da sua prova (ancora fixa, ignora disponibilidade).
        List<DayOfWeek> restantes = new ArrayList<>(provas);
        for (int i = 0; i < composed.size(); i++) {
            SessionSlot s = composed.get(i);
            if (TipoTreino.PROVA.name().equals(s.sessionType()) && !restantes.isEmpty()) {
                DayOfWeek dia = restantes.remove(0);
                result.set(i, comDia(s, dia));
                livres.remove(dia);
                diasComDura.add(dia);
            }
        }

        // D-1 de cada prova da semana — proibido para LONGO/duras (não so as ancoradas acima; a
        // lista completa, pre-mortem 2: "preferir longe" nao e "proibir adjacente"). A semana e
        // segunda→domingo: a vespera de uma prova na segunda esta no plano anterior, e
        // MONDAY.minus(1) seria o domingo 6 dias DEPOIS da prova (tasks.md 7.1).
        Set<DayOfWeek> proibidos = provas.stream()
                .filter(d -> d != DayOfWeek.MONDAY)
                .map(d -> d.minus(1))
                .collect(Collectors.toSet());

        // 2. Todo LONGO (chave primeiro, depois os demais — TAPER compoe LONGO sem chave).
        for (boolean somenteChave : new boolean[]{true, false}) {
            for (int i = 0; i < composed.size(); i++) {
                if (result.get(i) != null) continue;
                SessionSlot s = composed.get(i);
                if (!TipoTreino.LONGO.name().equals(s.sessionType())) continue;
                if (s.chave() != somenteChave) continue;

                List<DayOfWeek> candidatos = livres.stream().filter(d -> !proibidos.contains(d)).toList();
                DayOfWeek alvo = (preferredLongDay != null && candidatos.contains(preferredLongDay))
                        ? preferredLongDay
                        : (candidatos.isEmpty() ? null : candidatos.get(candidatos.size() - 1));
                if (alvo != null) {
                    result.set(i, comDia(s, alvo));
                    livres.remove(alvo);
                    diasComDura.add(alvo);
                }
            }
        }

        // 3. Demais sessoes duras: nunca no D-1 de uma prova; maximiza a distancia aos dias ja usados.
        for (int i = 0; i < composed.size(); i++) {
            SessionSlot s = composed.get(i);
            if (result.get(i) != null) continue;
            if (DURAS.contains(TipoTreino.valueOf(s.sessionType()))) {
                List<DayOfWeek> candidatos = livres.stream().filter(d -> !proibidos.contains(d)).toList();
                DayOfWeek melhor = escolherDiaEspacado(candidatos, diasComDura);
                if (melhor != null) {
                    result.set(i, comDia(s, melhor));
                    livres.remove(melhor);
                    diasComDura.add(melhor);
                }
            }
        }

        // 4. Demais slots — nunca LONGO/duras: as que nao couberam nos passos 2-3 ficam sem dia (o
        //    chamador decide), nunca recicladas aqui. Leves podem cair no D-1 (livres inteira).
        for (int i = 0; i < composed.size(); i++) {
            if (result.get(i) != null) continue;
            SessionSlot s = composed.get(i);
            TipoTreino tipo = TipoTreino.valueOf(s.sessionType());
            if (tipo == TipoTreino.LONGO || DURAS.contains(tipo)) {
                result.set(i, s); // day == null — slot inviavel, propositalmente nao reciclado aqui
                continue;
            }
            if (!livres.isEmpty()) {
                DayOfWeek d = livres.remove(0);
                result.set(i, comDia(s, d));
            } else {
                result.set(i, s); // sem dia livre — mantem null (nao deve ocorrer: slots ≤ dias)
            }
        }
        return result;
    }

    /** Escolhe o dia livre cuja menor distancia (com wrap dom→seg) aos dias de intensidade e maxima. */
    private DayOfWeek escolherDiaEspacado(List<DayOfWeek> livres, List<DayOfWeek> ancoras) {
        DayOfWeek melhor = null;
        int melhorDist = -1;
        for (DayOfWeek d : livres) { // livres ja esta em ordem canonica → desempate estavel
            int dist = ancoras.isEmpty() ? Integer.MAX_VALUE
                    : ancoras.stream().mapToInt(a -> distanciaCircular(d, a)).min().orElse(Integer.MAX_VALUE);
            if (dist > melhorDist) {
                melhorDist = dist;
                melhor = d;
            }
        }
        return melhor;
    }

    /** Distancia entre dois dias da semana com wrap (dom↔seg adjacentes) — 1 = consecutivos. */
    private int distanciaCircular(DayOfWeek a, DayOfWeek b) {
        int diff = Math.abs(a.getValue() - b.getValue());
        return Math.min(diff, 7 - diff);
    }

    private SessionSlot comDia(SessionSlot s, DayOfWeek dia) {
        return new SessionSlot(dia, s.sessionType(), s.targetTss(), s.intensityZone(), s.chave(), s.durationMinutes());
    }
}
