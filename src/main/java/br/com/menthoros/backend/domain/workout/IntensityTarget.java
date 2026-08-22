package br.com.menthoros.backend.domain.workout;

/**
 * Meta de intensidade de uma etapa — <b>uma</b> por etapa, nunca duas.
 *
 * <p>Espelha o eixo "Meta de intensidade → Tipo" do criador de treinos do Garmin, que é um dropdown
 * de escolha única (validado na UI em 2026-08-02). Modelar como campos opcionais acumuláveis, e
 * resolver a exclusividade por precedência no chamador, diverge do domínio que integramos: nada
 * estrutural impediria o serializador de emitir pace e FC no mesmo step, e o alvo que o relógio
 * usaria passaria a depender de ordem de código.</p>
 *
 * <p>{@link NoTarget} é estado de primeira classe, não ausência de dado: "Sem objetivo" é a primeira
 * opção do dropdown e uma prescrição que o treinador faz deliberadamente.</p>
 */
public sealed interface IntensityTarget permits IntensityTarget.NoTarget, PaceTarget, HrTarget {

    /** Etapa sem meta de intensidade — o relógio não controla nada, e isso é prescrição válida. */
    record NoTarget() implements IntensityTarget {
    }

    IntensityTarget SEM_OBJETIVO = new NoTarget();
}
