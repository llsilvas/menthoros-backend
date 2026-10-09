package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.WaitlistFunnelBucketOutputDto;
import br.com.menthoros.backend.entity.FoundingInvite;
import br.com.menthoros.backend.entity.Waitlist;
import br.com.menthoros.backend.enums.PerfilWaitlist;
import br.com.menthoros.backend.repository.FoundingInviteRepository;
import br.com.menthoros.backend.repository.WaitlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaitlistFunnelServiceImplTest {

    @Mock
    private WaitlistRepository waitlistRepository;

    @Mock
    private FoundingInviteRepository foundingInviteRepository;

    private WaitlistFunnelServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WaitlistFunnelServiceImpl(waitlistRepository, foundingInviteRepository);
    }

    private Waitlist waitlist(PerfilWaitlist perfil, String utmSource, String utmContent, Instant createdAt) {
        return Waitlist.builder()
                .id(UUID.randomUUID())
                .nome("Lead")
                .email(UUID.randomUUID() + "@exemplo.com")
                .emailNormalized(UUID.randomUUID() + "@exemplo.com")
                .perfil(perfil)
                .aceiteLgpd(true)
                .utmSource(utmSource)
                .utmContent(utmContent)
                .createdAt(createdAt)
                .build();
    }

    private FoundingInvite convite(UUID waitlistId, boolean invalidado, boolean convertido) {
        return FoundingInvite.builder()
                .id(UUID.randomUUID())
                .waitlistId(waitlistId)
                .tokenHash("hash-" + UUID.randomUUID())
                .email("convidado@exemplo.com")
                .invitedBy("admin")
                .invalidatedAt(invalidado ? Instant.now().atOffset(java.time.ZoneOffset.UTC) : null)
                .convertedAt(convertido ? Instant.now().atOffset(java.time.ZoneOffset.UTC) : null)
                .build();
    }

    @Test
    void agrupaPorUtmSourceEUtmContent() {
        Instant agora = Instant.now();
        Waitlist bio1 = waitlist(PerfilWaitlist.TREINADOR, "instagram", "bio-link", agora);
        Waitlist bio2 = waitlist(PerfilWaitlist.ATLETA, "instagram", "bio-link", agora);
        Waitlist post1 = waitlist(PerfilWaitlist.TREINADOR, "instagram", "post-fixado", agora);
        Waitlist post2 = waitlist(PerfilWaitlist.TREINADOR, "instagram", "post-fixado", agora);
        when(waitlistRepository.findAll()).thenReturn(List.of(bio1, bio2, post1, post2));
        when(foundingInviteRepository.findAll()).thenReturn(List.of());

        List<WaitlistFunnelBucketOutputDto> resultado = service.calcularFunil(null, null);

        assertThat(resultado).hasSize(2);
        WaitlistFunnelBucketOutputDto bioLink = resultado.stream()
                .filter(b -> "bio-link".equals(b.utmContent())).findFirst().orElseThrow();
        assertThat(bioLink.total()).isEqualTo(2);
        assertThat(bioLink.qualified()).isEqualTo(1);

        WaitlistFunnelBucketOutputDto postFixado = resultado.stream()
                .filter(b -> "post-fixado".equals(b.utmContent())).findFirst().orElseThrow();
        assertThat(postFixado.total()).isEqualTo(2);
        assertThat(postFixado.qualified()).isEqualTo(2);
    }

    @Test
    void conviteNaoInvalidadoContaComoInvited() {
        Waitlist lead = waitlist(PerfilWaitlist.TREINADOR, "instagram", "bio-link", Instant.now());
        when(waitlistRepository.findAll()).thenReturn(List.of(lead));
        when(foundingInviteRepository.findAll()).thenReturn(List.of(convite(lead.getId(), false, false)));

        List<WaitlistFunnelBucketOutputDto> resultado = service.calcularFunil(null, null);

        assertThat(resultado.get(0).invited()).isEqualTo(1);
        assertThat(resultado.get(0).active()).isZero();
    }

    @Test
    void conviteConvertidoContaComoActiveEInvited() {
        Waitlist lead = waitlist(PerfilWaitlist.TREINADOR, "instagram", "bio-link", Instant.now());
        when(waitlistRepository.findAll()).thenReturn(List.of(lead));
        when(foundingInviteRepository.findAll()).thenReturn(List.of(convite(lead.getId(), false, true)));

        List<WaitlistFunnelBucketOutputDto> resultado = service.calcularFunil(null, null);

        assertThat(resultado.get(0).invited()).isEqualTo(1);
        assertThat(resultado.get(0).active()).isEqualTo(1);
    }

    @Test
    void conviteInvalidadoNaoContaComoInvitedNemActive() {
        Waitlist lead = waitlist(PerfilWaitlist.TREINADOR, "instagram", "bio-link", Instant.now());
        when(waitlistRepository.findAll()).thenReturn(List.of(lead));
        when(foundingInviteRepository.findAll()).thenReturn(List.of(convite(lead.getId(), true, false)));

        List<WaitlistFunnelBucketOutputDto> resultado = service.calcularFunil(null, null);

        assertThat(resultado.get(0).invited()).isZero();
        assertThat(resultado.get(0).active()).isZero();
    }

    @Test
    void semUtmAgrupaNoGrupoNulo() {
        Waitlist lead = waitlist(PerfilWaitlist.TREINADOR, null, null, Instant.now());
        when(waitlistRepository.findAll()).thenReturn(List.of(lead));
        when(foundingInviteRepository.findAll()).thenReturn(List.of());

        List<WaitlistFunnelBucketOutputDto> resultado = service.calcularFunil(null, null);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).utmSource()).isNull();
        assertThat(resultado.get(0).total()).isEqualTo(1);
    }

    @Test
    void filtraPorPeriodo() {
        Instant agora = Instant.now();
        Waitlist dentro = waitlist(PerfilWaitlist.TREINADOR, "instagram", "bio-link", agora);
        Waitlist antes = waitlist(PerfilWaitlist.TREINADOR, "instagram", "bio-link", agora.minus(10, ChronoUnit.DAYS));
        when(waitlistRepository.findAll()).thenReturn(List.of(dentro, antes));
        when(foundingInviteRepository.findAll()).thenReturn(List.of());

        List<WaitlistFunnelBucketOutputDto> resultado = service.calcularFunil(agora.minus(1, ChronoUnit.DAYS), null);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).total()).isEqualTo(1);
    }

    @Test
    void periodoEhInclusivoNasBordas() {
        Instant desde = Instant.parse("2026-01-01T00:00:00Z");
        Instant ate = Instant.parse("2026-01-31T23:59:59Z");
        Waitlist naBordaDeBaixo = waitlist(PerfilWaitlist.TREINADOR, "instagram", "bio-link", desde);
        Waitlist naBordaDeCima = waitlist(PerfilWaitlist.TREINADOR, "instagram", "bio-link", ate);
        Waitlist foraAntes = waitlist(PerfilWaitlist.TREINADOR, "instagram", "bio-link", desde.minusSeconds(1));
        Waitlist foraDepois = waitlist(PerfilWaitlist.TREINADOR, "instagram", "bio-link", ate.plusSeconds(1));
        when(waitlistRepository.findAll()).thenReturn(List.of(naBordaDeBaixo, naBordaDeCima, foraAntes, foraDepois));
        when(foundingInviteRepository.findAll()).thenReturn(List.of());

        List<WaitlistFunnelBucketOutputDto> resultado = service.calcularFunil(desde, ate);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).total()).isEqualTo(2);
    }
}
