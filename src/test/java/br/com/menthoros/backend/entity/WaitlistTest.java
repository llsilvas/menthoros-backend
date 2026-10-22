package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.PerfilWaitlist;
import br.com.menthoros.backend.enums.WaitlistStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Waitlist.getStatus() — derivado de timestamps")
class WaitlistTest {

    private Waitlist.WaitlistBuilder base() {
        return Waitlist.builder()
                .nome("Maria").email("maria@exemplo.com").emailNormalized("maria@exemplo.com")
                .perfil(PerfilWaitlist.TREINADOR).aceiteLgpd(true);
    }

    @Test
    @DisplayName("sem nenhum timestamp -> NEW")
    void semTimestampsEhNew() {
        assertThat(base().build().getStatus()).isEqualTo(WaitlistStatus.NEW);
    }

    @Test
    @DisplayName("invitedAt preenchido, sem o resto -> INVITED")
    void comInvitedAtEhInvited() {
        Waitlist lead = base().invitedAt(Instant.now()).build();
        assertThat(lead.getStatus()).isEqualTo(WaitlistStatus.INVITED);
    }

    @Test
    @DisplayName("activatedAt preenchido -> ACTIVE, mesmo com invitedAt também preenchido")
    void comActivatedAtEhActive() {
        Waitlist lead = base().invitedAt(Instant.now()).activatedAt(Instant.now())
                .assessoriaId(UUID.randomUUID()).build();
        assertThat(lead.getStatus()).isEqualTo(WaitlistStatus.ACTIVE);
    }

    @Test
    @DisplayName("discardedAt tem precedência sobre todos os outros — terminal")
    void discardedAtTemPrecedencia() {
        Waitlist lead = base()
                .invitedAt(Instant.now()).activatedAt(Instant.now()).assessoriaId(UUID.randomUUID())
                .discardedAt(Instant.now())
                .build();
        assertThat(lead.getStatus()).isEqualTo(WaitlistStatus.DISCARDED);
    }
}
