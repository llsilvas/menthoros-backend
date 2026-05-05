package br.com.menthoros.backend.repository.custom;

import br.com.menthoros.backend.entity.Atleta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Custom repository interface for {@link Atleta} providing EntityGraph-based fetch strategies.
 *
 * <p>Fetch types supported:
 * <ul>
 *   <li>{@code "basic"} — only base entity fields (no collections)</li>
 *   <li>{@code "dias"} — includes {@code diasDisponiveis}</li>
 *   <li>{@code "provas"} — includes {@code provas}</li>
 *   <li>{@code "all"} — includes both {@code diasDisponiveis} and {@code provas}</li>
 * </ul>
 */
public interface AtletaRepositoryCustom {

    /**
     * Find atletas with a specific EntityGraph fetch configuration.
     *
     * @param tenantId  the tenant (assessoria) ID
     * @param fetchType the fetch strategy: "basic", "dias", "provas", or "all"
     * @param pageable  pagination info
     * @return page of atletas
     */
    Page<Atleta> findAtletasWithFetchGraph(UUID tenantId, String fetchType, Pageable pageable);
}
