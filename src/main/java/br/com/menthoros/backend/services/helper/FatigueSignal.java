package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.FatigueSignalType;
import org.jspecify.annotations.Nullable;

/**
 * Um sinal de fadiga detectado, com o número que o disparou e a régua usada — o prompt cita os dois
 * ("TSB −18, limiar −15") para o treinador conferir sem abrir outra tela.
 *
 * @param type      qual sinal
 * @param value     valor observado ({@code null} quando o sinal é booleano, como o check-in)
 * @param threshold limiar que o valor cruzou ({@code null} no mesmo caso)
 */
public record FatigueSignal(FatigueSignalType type, @Nullable Double value, @Nullable Double threshold) {

    public FatigueSignal {
        if (type == null) {
            throw new IllegalArgumentException("FatigueSignal.type não pode ser nulo");
        }
    }

    public static FatigueSignal de(FatigueSignalType type, double value, double threshold) {
        return new FatigueSignal(type, value, threshold);
    }

    public static FatigueSignal de(FatigueSignalType type) {
        return new FatigueSignal(type, null, null);
    }

    public boolean liberaDescanso() {
        return type.liberaDescanso();
    }
}
