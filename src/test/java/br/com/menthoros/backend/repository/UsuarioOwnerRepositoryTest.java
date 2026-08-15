package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rede de proteção do achado que reprovou o DoR desta change.
 *
 * <p>{@code Usuario.role} guarda um único valor. A tentação, ao introduzir a role
 * {@code PROPRIETARIO}, é resolvê-la em {@code mapToUserRole} e gravá-la em {@code role} — e isso
 * <b>tiraria o dono da contagem de técnicos do plano</b>, que no BASIC tem
 * {@code maxTecnicos = 1}. O efeito seria uma assessoria recém-criada aparecendo com zero técnicos,
 * e a checagem de capacidade liberando um segundo técnico que o plano não comporta.
 *
 * <p>Estes testes fixam o comportamento correto contra o banco real: o dono é
 * {@code role = TECNICO} + {@code owner = true}, contado como técnico e distinguível como dono.
 */
@Transactional
class UsuarioOwnerRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    @Nested
    @DisplayName("contagem de técnicos do plano")
    class ContagemDeTecnicos {

        @Test
        @DisplayName("o dono continua contando como técnico")
        void donoContaComoTecnico() {
            Assessoria assessoria = seedAssessoria();
            seedUsuario(assessoria, UserRole.TECNICO, true);

            Long tecnicos = usuarioRepository.countByTenantIdAndRoleAndAtivoTrue(
                    assessoria.getId(), UserRole.TECNICO);

            assertThat(tecnicos).isEqualTo(1L);
        }

        @Test
        @DisplayName("dono e técnico contratado somam dois")
        void donoMaisContratado() {
            Assessoria assessoria = seedAssessoria();
            seedUsuario(assessoria, UserRole.TECNICO, true);
            seedUsuario(assessoria, UserRole.TECNICO, false);

            Long tecnicos = usuarioRepository.countByTenantIdAndRoleAndAtivoTrue(
                    assessoria.getId(), UserRole.TECNICO);

            assertThat(tecnicos).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("métodos de conveniência da entidade")
    class MetodosDeConveniencia {

        @Test
        @DisplayName("o dono segue como técnico e com permissão de escrita")
        void donoMantemCapacidadesDeTecnico() {
            Usuario dono = seedUsuario(seedAssessoria(), UserRole.TECNICO, true);

            assertThat(dono.isTecnico()).isTrue();
            assertThat(dono.podeEscrever()).isTrue();
            assertThat(dono.isOwner()).isTrue();
        }

        @Test
        @DisplayName("técnico contratado não é dono")
        void contratadoNaoEhDono() {
            Usuario contratado = seedUsuario(seedAssessoria(), UserRole.TECNICO, false);

            assertThat(contratado.isTecnico()).isTrue();
            assertThat(contratado.isOwner()).isFalse();
        }
    }

    @Test
    @DisplayName("a coluna owner nasce false para quem não é dono")
    void defaultDaColunaEhFalse() {
        Assessoria assessoria = seedAssessoria();
        UUID id = UUID.randomUUID();
        Usuario semFlag = usuarioRepository.save(Usuario.builder()
                .id(id)
                .keycloakId(id.toString())
                .nome("Sem flag")
                .email("sem-flag-" + id + "@exemplo.com")
                .role(UserRole.TECNICO)
                .ativo(true)
                .assessoria(assessoria)
                .build());

        assertThat(semFlag.isOwner()).isFalse();
    }

    private Assessoria seedAssessoria() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Owner Test");
        assessoria.setDominio("owner-test-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        return assessoriaRepository.save(assessoria);
    }

    private Usuario seedUsuario(Assessoria assessoria, UserRole role, boolean owner) {
        UUID id = UUID.randomUUID();
        return usuarioRepository.save(Usuario.builder()
                .id(id)
                .keycloakId(id.toString())
                .nome(owner ? "Dono" : "Contratado")
                .email("usuario-" + id + "@exemplo.com")
                .role(role)
                .owner(owner)
                .ativo(true)
                .assessoria(assessoria)
                .build());
    }
}
