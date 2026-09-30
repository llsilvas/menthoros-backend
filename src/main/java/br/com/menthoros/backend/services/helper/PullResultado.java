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

    /**
     * O que o coach vê em {@code lastSyncError} quando o ciclo terminou sem erro mas deixou atividade
     * para trás — sem isso, um descarte passaria por sync bem-sucedido.
     */
    public @Nullable String avisoDeIgnoradas() {
        return ignoradas > 0
                ? ignoradas + " atividade(s) não importada(s) neste ciclo (dados inválidos ou falha recorrente)"
                : null;
    }
}
