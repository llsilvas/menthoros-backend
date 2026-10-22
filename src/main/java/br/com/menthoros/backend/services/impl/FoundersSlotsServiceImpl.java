package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.FoundersSlotsOutputDto;
import br.com.menthoros.backend.repository.FoundingInviteRepository;
import br.com.menthoros.backend.services.FoundersSlotsService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Cache próprio, em processo, com TTL curto — <strong>não</strong> usa o {@code CacheManager}
 * compartilhado de {@code CacheConfig}: todos os caches nomeados ali herdam o {@code defaultTtl}
 * único de 30 minutos (TTL por cache não implementado, ver comentário em {@code CacheConfig}), o
 * que deixaria um convite novo invisível por meia hora. Mesmo padrão de cache local do
 * {@code PublicEndpointRateLimitFilter}.
 *
 * <p>Sem réplica compartilhada: com mais de uma instância do backend, {@code taken} pode divergir
 * até o TTL entre réplicas. Aceitável no volume atual (10 vagas, convites raros).</p>
 */
@Service
public class FoundersSlotsServiceImpl implements FoundersSlotsService {

    private static final String CHAVE_UNICA = "vagas";

    private final FoundingInviteRepository foundingInviteRepository;
    private final int totalSlots;
    private final Cache<String, FoundersSlotsOutputDto> cache;

    public FoundersSlotsServiceImpl(
            FoundingInviteRepository foundingInviteRepository,
            @Value("${app.founding-invite.total-slots:10}") int totalSlots,
            // Configurável para o IT conseguir desligar o cache (TTL ínfimo) entre asserções
            // sequenciais sem esperar a janela real — produção fica no default de 30s.
            @Value("${app.founding-invite.slots-cache-ttl:PT30S}") Duration cacheTtl) {
        this.foundingInviteRepository = foundingInviteRepository;
        this.totalSlots = totalSlots;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtl)
                .maximumSize(1)
                .build();
    }

    /**
     * Idempotent: YES — leitura, sem mutação de estado.
     * Side Effects: NONE
     * Tenant-aware: NO — dado público/global, sem escopo de tenant (ver proposal.md).
     */
    @Override
    public FoundersSlotsOutputDto obterVagas() {
        return cache.get(CHAVE_UNICA, chave -> calcular());
    }

    private FoundersSlotsOutputDto calcular() {
        long ocupadas = foundingInviteRepository.countByInvalidatedAtIsNull();
        int taken = (int) Math.min(ocupadas, Integer.MAX_VALUE);
        int remaining = Math.max(totalSlots - taken, 0);
        return new FoundersSlotsOutputDto(totalSlots, taken, remaining, remaining > 0);
    }
}
