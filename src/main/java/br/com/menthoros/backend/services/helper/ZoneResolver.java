package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.v2.Zona;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaFC;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaPace;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resolve {@link Zona} (schema v2, enum fechado) em faixas absolutas de FC/pace, delegando para
 * {@link ZonaTreinoService} — o mesmo serviço calibrado (modelo LTHR/Friel) que
 * {@code PlanoLlmValidator} (FC) e {@code PaceZoneCalculator} já usam hoje. Não é uma extração de
 * {@code TreinoNormalizador.zonaParaFc}: aquele método faz parsing de zona em texto livre da LLM
 * (com casos de borda documentados — range colapsado, sem caso para "LIMIAR"); {@code ZoneResolver}
 * recebe um enum já fechado, sem parsing (semantic-session-schema, design.md Decisão 2 e 6).
 *
 * <p>Idempotent: YES — cálculo puro sobre os parâmetros recebidos, sem I/O.
 * Side Effects: NONE. Tenant-aware: NO.</p>
 */
@Component
@RequiredArgsConstructor
public class ZoneResolver {

    /**
     * paceLimiar sintético usado só quando o atleta não tem {@code paceLimiar} cadastrado
     * (campo sem fallback calculado, ao contrário de FC — ver {@code Atleta.getFcLimiarCalculada()}).
     * Implícito pelos defaults fixos que v1 já usa: {@code PACE_Z2_DEFAULT_MIN_KM=7.0} ÷
     * {@code FATOR_PACE_Z2=1.20} ≈ 5.83 (TreinoNormalizador.java:38-41). Sentinela de último
     * recurso — na prática quase nunca exercitado (a maioria dos atletas tem `fcMaxima` calculada
     * por idade, mas `paceLimiar` não tem fallback equivalente).
     */
    private static final BigDecimal PACE_LIMIAR_FALLBACK_MIN_KM = BigDecimal.valueOf(5.83);

    private final ZonaTreinoService zonaTreinoService;

    public record FaixaFc(int min, int max) {}

    public record FaixaPace(BigDecimal min, BigDecimal max) {}

    /**
     * Resolve a faixa de FC (bpm) de uma zona. {@code fcMaxima}/{@code fcLimiar} devem vir de
     * {@code Atleta.getFcMaximaCalculada()}/{@code getFcLimiarCalculada()} — accessors que nunca
     * retornam {@code null} (sempre têm fallback por idade/percentual), então não há caso de
     * FC ausente aqui, ao contrário de {@code paceLimiar}.
     */
    public FaixaFc bpm(Zona zona, Integer fcMaxima, Integer fcLimiar) {
        List<ZonaFC> zonas = zonaTreinoService.calcularZonasFC(fcMaxima, fcLimiar);
        ZonaFC z = zonas.get(zona.indice() - 1);
        return new FaixaFc(z.fcMin(), z.fcMax());
    }

    /**
     * Resolve a faixa de pace (min/km) de uma zona. Cai no fallback sintético se {@code paceLimiar}
     * for {@code null} <b>ou não-positivo</b> (achado do /qa: um `paceLimiar` cadastrado como 0 ou
     * negativo causaria divisão por zero/pace negativo em {@code SessionResolver} — o cadastro
     * inválido não é algo que um retry ao LLM corrige, então cai no mesmo fallback do dado ausente).
     */
    public FaixaPace pace(Zona zona, @Nullable BigDecimal paceLimiar) {
        BigDecimal limiar = (paceLimiar != null && paceLimiar.signum() > 0) ? paceLimiar : PACE_LIMIAR_FALLBACK_MIN_KM;
        List<ZonaPace> zonas = zonaTreinoService.calcularZonasPace(limiar);
        ZonaPace z = zonas.get(zona.indice() - 1);
        return new FaixaPace(z.paceMin(), z.paceMax());
    }
}
