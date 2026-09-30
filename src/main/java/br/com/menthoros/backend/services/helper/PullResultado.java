package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.ErroCategoriaPull;
import br.com.menthoros.backend.enums.ResultadoPull;
import org.jspecify.annotations.Nullable;

/**
 * O que um ciclo de pull de um atleta conseguiu (fix-sync-cursor-data-loss, design D5).
 * {@code insercoes} conta só registros novos já commitados — nunca atualização nem o vencedor de uma
 * corrida concorrente.
 */
public record PullResultado(ResultadoPull resultado, @Nullable ErroCategoriaPull erro, int insercoes, int ignoradas) {
}
