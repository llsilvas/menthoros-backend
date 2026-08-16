package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.output.UsuarioMeOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.UserRole;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Logo da assessoria no payload do {@code GET /users/me}.
 *
 * <p><b>Por que este teste existe.</b> A shell do coach (sidebar) monta a partir do {@code me}, e o
 * logo só era exposto por {@code GET /assessorias/me}, que apenas a tela de configurações consome —
 * então o upload funcionava e o logo não aparecia em lugar nenhum do produto.
 *
 * <p>A armadilha que ele trava: a entidade {@code Assessoria} tem um campo {@code logoUrl} legado,
 * que está {@code NULL} desde que o logo passou a ser um BLOB em {@code tb_assessoria_logo}. Mapear
 * aquele campo compilaria, passaria em qualquer teste de fumaça e devolveria {@code null} para todo
 * mundo — o bug intacto, com a correção declarada pronta.
 */
@DisplayName("UsuarioMapper — logo da assessoria no me")
class UsuarioMapperLogoTest {

    private final UsuarioMapper mapper = new UsuarioMapper();

    private static final String ROTA_DO_LOGO = "/api/v1/assessorias/me/logo";

    @Nested
    @DisplayName("toMeOutputDto")
    class ToMeOutputDto {

        @Test
        @DisplayName("com logo presente devolve a rota que serve a imagem, e a versão")
        void comLogo() {
            UsuarioMeOutputDto dto = mapper.toMeOutputDto(usuarioCom(assessoria(7L)), null, consentimento(), true);

            assertThat(dto.assessoria().temLogo()).isTrue();
            assertThat(dto.assessoria().logoUrl()).isEqualTo(ROTA_DO_LOGO);
            assertThat(dto.assessoria().version())
                    .as("sem a versão o navegador serve o logo antigo do cache após a troca")
                    .isEqualTo(7L);
        }

        @Test
        @DisplayName("sem logo devolve null na rota, não string vazia")
        void semLogo() {
            UsuarioMeOutputDto dto = mapper.toMeOutputDto(usuarioCom(assessoria(1L)), null, consentimento(), false);

            assertThat(dto.assessoria().temLogo()).isFalse();
            assertThat(dto.assessoria().logoUrl()).isNull();
        }

        /** Usuário sem tenant: o contrato já devolvia {@code null} e não pode passar a quebrar. */
        @Test
        @DisplayName("usuário sem assessoria continua com assessoria null")
        void semAssessoria() {
            Usuario usuario = usuarioCom(null);

            UsuarioMeOutputDto dto = mapper.toMeOutputDto(usuario, null, consentimento(), false);

            assertThat(dto.assessoria()).isNull();
        }

        @Test
        @DisplayName("os demais campos da assessoria seguem intactos")
        void naoRegridiuOResto() {
            Assessoria assessoria = assessoria(2L);

            UsuarioMeOutputDto dto = mapper.toMeOutputDto(usuarioCom(assessoria), null, consentimento(), true);

            assertThat(dto.assessoria().id()).isEqualTo(assessoria.getId());
            assertThat(dto.assessoria().nome()).isEqualTo("Corridas Serra");
            assertThat(dto.assessoria().dominio()).isEqualTo("corridas-serra");
        }
    }

    private Assessoria assessoria(Long version) {
        Assessoria a = new Assessoria();
        a.setId(UUID.randomUUID());
        a.setNome("Corridas Serra");
        a.setDominio("corridas-serra");
        a.setVersion(version);
        return a;
    }

    private Usuario usuarioCom(Assessoria assessoria) {
        return Usuario.builder()
                .id(UUID.randomUUID())
                .nome("Coach Teste")
                .email("coach@exemplo.com")
                .role(UserRole.TECNICO)
                .assessoria(assessoria)
                .build();
    }

    private LgpdConsentStatus consentimento() {
        return new LgpdConsentStatus(true, "2026-06-30", "2026-06-30",
                Instant.parse("2026-07-31T19:23:43Z"), "2026-06-30", "2026-06-30");
    }
}
