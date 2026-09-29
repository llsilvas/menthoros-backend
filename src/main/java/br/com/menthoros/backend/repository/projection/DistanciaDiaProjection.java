package br.com.menthoros.backend.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Km realizados num dia, já somados no banco — o perfil do coach só precisa do total, não dos
 * treinos (que trariam a coleção EAGER de sensações por linha).
 */
public interface DistanciaDiaProjection {
    LocalDate getDataTreino();
    BigDecimal getDistanciaKm();
}
