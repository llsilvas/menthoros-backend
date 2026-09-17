package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.MelhorEsforcoDto;

import java.util.List;
import java.util.UUID;

/**
 * Melhor tempo contínuo do atleta por distância de referência (400m-10k), lido do intervals.icu.
 */
public interface MelhorEsforcoService {

    /**
     * Idempotent: YES — leitura pura, sem mutação.
     * Side Effects: External API call (intervals.icu pace-curves), quando o atleta está conectado.
     * Tenant-aware: YES — resolve o tenant do chamador via {@code TenantContext}.
     *
     * @param atletaId atleta a consultar
     * @param janela   sintaxe de curva do intervals.icu ("42d", "1y", "all")
     * @return lista de marcas encontradas dentro da tolerância; vazia se o atleta não tem
     *         integração intervals.icu ativa, ou se nenhuma distância-alvo teve dado suficiente
     */
    List<MelhorEsforcoDto> buscar(UUID atletaId, String janela);
}
