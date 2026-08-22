package br.com.menthoros.backend.domain.workout;

/**
 * Alvo de frequência cardíaca de uma etapa, <b>sempre em bpm absoluto</b>.
 *
 * <p>É a forma "Frequência cardíaca personalizada" do Garmin — faixa de bpm que ninguém precisa
 * resolver. As outras duas formas do padrão são relativas e ambas erradas para nós: o canal
 * percentual é <b>%FCmax por definição do formato</b>, enquanto o domínio raciocina em %LTHR
 * (Friel), e a forma por zona delega a conversão às zonas configuradas no relógio, que o Menthoros
 * não escreve.</p>
 *
 * <p>Por isso percentual e zona não existem mais aqui: são representação intermediária do parser
 * ({@code IntervalsIcuTargetParser.FcAlvoBruto}), resolvidas contra o atleta antes de virar meta.
 * Um alvo relativo não tem como atravessar esta fronteira.</p>
 */
public record HrTarget(Integer startBpm, Integer endBpm) implements IntensityTarget {
}
