package br.com.menthoros.backend.repository.projection;

import org.jspecify.annotations.Nullable;

/** Só o que o endpoint de trajeto precisa (add-activity-gps-trace, D4), sem carregar a entidade. */
public interface GpsTraceView {
    @Nullable String getGpsTrace();
    @Nullable String getDeviceName();
}
