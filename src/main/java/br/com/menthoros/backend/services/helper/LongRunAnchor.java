package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.enums.DiaSemana;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Coloca o treino LONGO no dia preferido do atleta trocando-o de dia com quem estiver lá.
 *
 * <p>A âncora era da {@code RedistribuicaoTreinoHelper}, que deixa de rodar quando a cobertura da
 * semana é validada — ela descartava treino por conflito de dias consecutivos e reabriria um dia
 * omitido (add-descanso-explicito-por-fadiga, Decisão 6). A troca preserva a cobertura: mesmos dias,
 * mesmos itens, nunca descarta.</p>
 *
 * <p>Roda <b>antes</b> do {@link WeeklyCoverageValidator}, dentro do turno de reparo, para o
 * validador julgar o arranjo final — se a troca criar dois intensos em dias vizinhos, isso vira
 * violação reparável em vez de passar batido.</p>
 *
 * <p>Idempotent: YES — reaplicar sobre um plano já ancorado é no-op. Side Effects: NONE.</p>
 */
@Slf4j
@Component
public class LongRunAnchor {

    private static final String LONGO = "LONGO";

    /**
     * @return plano com o LONGO no dia preferido, ou o mesmo plano quando não há troca segura
     */
    public @Nullable PlanoSemanalLlmDto ancorar(@Nullable PlanoSemanalLlmDto plano, WeeklyCoverageContext ctx) {
        if (plano == null || plano.treinosPlanejados() == null || plano.treinosPlanejados().isEmpty()) {
            return plano;
        }
        DiaSemana preferido = ctx.diaPreferidoLongo();
        if (preferido == null || !ctx.effectiveDays().contains(preferido)) {
            return plano;
        }
        // Descanso está preso ao dia que o sinal libera (o primeiro dia efetivo, ou os dias da
        // sequência longa) — trocá-lo de lugar o invalidaria.
        boolean preferidoTemDescanso = plano.restDays().stream()
                .anyMatch(d -> preferido.name().equalsIgnoreCase(d.dayOfWeek()));
        if (preferidoTemDescanso) {
            return plano;
        }

        List<TreinoPlanejadoLlmDto> treinos = plano.treinosPlanejados();
        List<TreinoPlanejadoLlmDto> longos = treinos.stream()
                .filter(t -> LONGO.equalsIgnoreCase(t.tipoTreino()))
                .toList();
        if (longos.size() != 1) {
            return plano; // sem longo, ou ambíguo — a cobertura e os gates decidem
        }
        TreinoPlanejadoLlmDto longo = longos.getFirst();
        if (preferido.name().equalsIgnoreCase(longo.diaSemana())) {
            return plano;
        }

        Optional<TreinoPlanejadoLlmDto> ocupante = treinos.stream()
                .filter(t -> preferido.name().equalsIgnoreCase(t.diaSemana()))
                .findFirst();
        if (ocupante.isEmpty()) {
            return plano; // dia preferido vazio: a cobertura acusa o buraco, não é papel da âncora
        }

        String diaDoLongo = longo.diaSemana();
        TreinoPlanejadoLlmDto outro = ocupante.get();
        List<TreinoPlanejadoLlmDto> ajustados = treinos.stream()
                .map(t -> {
                    if (t == longo) return t.comDiaSemana(preferido.name());
                    if (t == outro) return t.comDiaSemana(diaDoLongo);
                    return t;
                })
                .toList();

        log.info("ÂNCORA DO LONGO: LONGO {} → {} (troca com {})", diaDoLongo, preferido.name(), outro.tipoTreino());
        return plano.toBuilder().treinosPlanejados(ajustados).build();
    }
}
