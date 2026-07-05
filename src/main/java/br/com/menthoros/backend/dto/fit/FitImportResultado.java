package br.com.menthoros.backend.dto.fit;

import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;

/**
 * Resultado interno de um import de .fit — carrega se o registro é novo (para o controller
 * decidir 201) ou já existia (200), já que {@code saveIdempotent} sozinho não diferencia os
 * dois casos (D0.8).
 */
public record FitImportResultado(TreinoRealizadoOutputDto treino, boolean novo) {}
