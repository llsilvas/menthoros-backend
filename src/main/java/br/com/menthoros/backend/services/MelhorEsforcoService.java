package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.MelhorEsforcoDto;
import br.com.menthoros.backend.dto.output.MelhoresEsforcosOutputDto;

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

    /**
     * Como {@link #buscar}, mas também informa se o atleta tem integração intervals.icu ativa —
     * usado pela tela do atleta pra distinguir "sem integração" (CTA de conexão) de "conectado mas
     * sem dado suficiente" (marcas vazias, sem CTA).
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: External API call (intervals.icu pace-curves), quando conectado.
     * Tenant-aware: YES.
     */
    MelhoresEsforcosOutputDto buscarParaAtleta(UUID atletaId, String janela);
}
