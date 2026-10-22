package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.FoundingInvite;
import br.com.menthoros.backend.entity.Waitlist;
import br.com.menthoros.backend.enums.PerfilWaitlist;
import br.com.menthoros.backend.repository.FoundingInviteRepository;
import br.com.menthoros.backend.repository.WaitlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test do endpoint público {@code GET /api/v1/founders/slots}.
 *
 * <p>{@code total-slots=3} fixado para exercitar o teto sem criar dezenas de convites.
 * {@code slots-cache-ttl} quase zero: o serviço é um singleton com cache de ~30s em produção, e os
 * métodos deste IT rodam no mesmo contexto Spring — sem isso, o 2º teste veria o valor cacheado do
 * 1º.</p>
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.founding-invite.total-slots=3",
        "app.founding-invite.slots-cache-ttl=PT0.001S"
})
@DisplayName("GET /api/v1/founders/slots")
class FoundersSlotsControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FoundingInviteRepository foundingInviteRepository;

    @Autowired
    private WaitlistRepository waitlistRepository;

    @BeforeEach
    void limparConvites() {
        foundingInviteRepository.deleteAll();
        waitlistRepository.deleteAll();
    }

    /** {@code tb_founding_invite.waitlist_id} tem FK para {@code tb_waitlist} — precisa existir. */
    private UUID inscritoNaWaitlist() {
        String email = "inscrito-" + UUID.randomUUID() + "@exemplo.com";
        Waitlist waitlist = Waitlist.builder()
                .nome("Treinador Teste")
                .email(email)
                .emailNormalized(email)
                .perfil(PerfilWaitlist.TREINADOR)
                .aceiteLgpd(true)
                .build();
        return waitlistRepository.save(waitlist).getId();
    }

    private FoundingInvite convite(boolean invalidado) {
        // Sem .id(...): a entidade usa @GeneratedValue, e um id manual faz Spring Data tratar o
        // save() como merge() de uma linha existente (falha com StaleObjectStateException, já que
        // a linha não existe de verdade) em vez de insert.
        FoundingInvite.FoundingInviteBuilder builder = FoundingInvite.builder()
                .waitlistId(inscritoNaWaitlist())
                .tokenHash("hash-" + UUID.randomUUID())
                .email("convidado-" + UUID.randomUUID() + "@exemplo.com")
                .expiresAt(OffsetDateTime.now().plusDays(5))
                .sentAt(OffsetDateTime.now())
                .invitedBy("admin")
                .createdAt(OffsetDateTime.now());
        if (invalidado) {
            builder.invalidatedAt(OffsetDateTime.now());
        }
        return builder.build();
    }

    @Test
    @DisplayName("sem convites, todas as vagas estão abertas")
    void semConvites() throws Exception {
        mockMvc.perform(get("/api/v1/founders/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.taken").value(0))
                .andExpect(jsonPath("$.remaining").value(3))
                .andExpect(jsonPath("$.open").value(true));
    }

    @Test
    @DisplayName("convite invalidado não ocupa vaga")
    void conviteInvalidadoNaoConta() throws Exception {
        foundingInviteRepository.save(convite(false));
        foundingInviteRepository.save(convite(true));

        mockMvc.perform(get("/api/v1/founders/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taken").value(1))
                .andExpect(jsonPath("$.remaining").value(2));
    }

    @Test
    @DisplayName("vagas esgotadas: remaining 0 e open false, nunca negativo")
    void vagasEsgotadas() throws Exception {
        foundingInviteRepository.save(convite(false));
        foundingInviteRepository.save(convite(false));
        foundingInviteRepository.save(convite(false));
        foundingInviteRepository.save(convite(false)); // 4ª, além do total de 3

        mockMvc.perform(get("/api/v1/founders/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taken").value(4))
                .andExpect(jsonPath("$.remaining").value(0))
                .andExpect(jsonPath("$.open").value(false));
    }
}
