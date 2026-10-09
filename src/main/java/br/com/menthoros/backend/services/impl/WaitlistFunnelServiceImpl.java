package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.WaitlistFunnelBucketOutputDto;
import br.com.menthoros.backend.entity.FoundingInvite;
import br.com.menthoros.backend.entity.Waitlist;
import br.com.menthoros.backend.enums.PerfilWaitlist;
import br.com.menthoros.backend.repository.FoundingInviteRepository;
import br.com.menthoros.backend.repository.WaitlistRepository;
import br.com.menthoros.backend.services.WaitlistFunnelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Correlaciona {@link Waitlist} e {@link FoundingInvite} em memória, sem SQL agregado — a waitlist
 * tem dezenas a centenas de linhas nesta escala, e uma query agregada complexa não paga o custo de
 * manutenção. Revisitar com SQL de verdade se o volume crescer para milhares de linhas.
 */
@Service
@RequiredArgsConstructor
public class WaitlistFunnelServiceImpl implements WaitlistFunnelService {

    private final WaitlistRepository waitlistRepository;
    private final FoundingInviteRepository foundingInviteRepository;

    private record ChaveUtm(String utmSource, String utmContent) {
    }

    /**
     * Idempotent: YES — leitura, sem mutação de estado.
     * Side Effects: NONE
     * Tenant-aware: NO — Waitlist/FoundingInvite são entidades globais, sem tenant.
     */
    @Override
    public List<WaitlistFunnelBucketOutputDto> calcularFunil(Instant desde, Instant ate) {
        List<Waitlist> leads = waitlistRepository.findAll().stream()
                .filter(w -> desde == null || !w.getCreatedAt().isBefore(desde))
                .filter(w -> ate == null || !w.getCreatedAt().isAfter(ate))
                .toList();

        Map<UUID, List<FoundingInvite>> convitesPorInscrito = foundingInviteRepository.findAll().stream()
                .collect(Collectors.groupingBy(FoundingInvite::getWaitlistId));

        Map<ChaveUtm, List<Waitlist>> leadsPorUtm = leads.stream()
                .collect(Collectors.groupingBy(w -> new ChaveUtm(w.getUtmSource(), w.getUtmContent())));

        return leadsPorUtm.entrySet().stream()
                .map(entry -> bucket(entry.getKey(), entry.getValue(), convitesPorInscrito))
                .sorted(Comparator.comparing((WaitlistFunnelBucketOutputDto b) -> b.total()).reversed()
                        // Desempate determinístico: sem isso, grupos com o mesmo total podem trocar
                        // de ordem entre chamadas (groupingBy não garante ordem estável), ruim numa
                        // tela/export que a pessoa vai comparar entre requisições.
                        .thenComparing(WaitlistFunnelBucketOutputDto::utmSource, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(WaitlistFunnelBucketOutputDto::utmContent, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    private WaitlistFunnelBucketOutputDto bucket(
            ChaveUtm chave, List<Waitlist> leads, Map<UUID, List<FoundingInvite>> convitesPorInscrito) {
        int total = leads.size();
        int qualified = (int) leads.stream().filter(w -> w.getPerfil() == PerfilWaitlist.TREINADOR).count();
        int invited = (int) leads.stream().filter(w -> temConviteNaoInvalidado(w, convitesPorInscrito)).count();
        int active = (int) leads.stream().filter(w -> temConviteConvertido(w, convitesPorInscrito)).count();
        return new WaitlistFunnelBucketOutputDto(chave.utmSource(), chave.utmContent(), total, qualified, invited, active);
    }

    private boolean temConviteNaoInvalidado(Waitlist lead, Map<UUID, List<FoundingInvite>> convitesPorInscrito) {
        return convitesPorInscrito.getOrDefault(lead.getId(), List.of()).stream()
                .anyMatch(c -> c.getInvalidatedAt() == null);
    }

    private boolean temConviteConvertido(Waitlist lead, Map<UUID, List<FoundingInvite>> convitesPorInscrito) {
        return convitesPorInscrito.getOrDefault(lead.getId(), List.of()).stream()
                .anyMatch(c -> c.getConvertedAt() != null);
    }
}
