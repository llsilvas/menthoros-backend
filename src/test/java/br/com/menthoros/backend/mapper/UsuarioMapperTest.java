package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.output.UsuarioMeOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsuarioMapperTest {

    private UsuarioMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new UsuarioMapper();
    }

    @Nested
    @DisplayName("toMeOutputDto")
    class ToMeOutputDto {

        @Test
        @DisplayName("lança IllegalArgumentException quando usuario é null")
        void rejeitaUsuarioNull() {
            assertThatThrownBy(() -> mapper.toMeOutputDto(null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Usuario");
        }

        @Test
        @DisplayName("mapeia campos básicos e assessoria sem atletaId quando Atleta é null")
        void mapeiaSemAtleta() {
            Usuario usuario = usuarioTecnico();

            UsuarioMeOutputDto dto = mapper.toMeOutputDto(usuario, null);

            assertThat(dto.id()).isEqualTo(usuario.getId());
            assertThat(dto.nome()).isEqualTo("João Silva"); // nome completo (nome + sobrenome)
            assertThat(dto.email()).isEqualTo("joao.silva@exemplo.com");
            assertThat(dto.role()).isEqualTo(UserRole.TECNICO);
            assertThat(dto.atletaId()).isNull();
            assertThat(dto.assessoria()).isNotNull();
            assertThat(dto.assessoria().id()).isEqualTo(usuario.getAssessoria().getId());
            assertThat(dto.assessoria().nome()).isEqualTo("Corridas Serra");
            assertThat(dto.assessoria().dominio()).isEqualTo("corridasserra");
        }

        @Test
        @DisplayName("preenche atletaId quando Atleta presente")
        void preencheAtletaId() {
            Usuario usuario = usuarioAtleta();
            Atleta atleta = Atleta.builder().id(UUID.randomUUID()).nome("Atleta").build();

            UsuarioMeOutputDto dto = mapper.toMeOutputDto(usuario, atleta);

            assertThat(dto.role()).isEqualTo(UserRole.ATLETA);
            assertThat(dto.atletaId()).isEqualTo(atleta.getId());
        }

        @Test
        @DisplayName("omite assessoria quando usuario não tem assessoria")
        void omiteAssessoriaQuandoNula() {
            Usuario usuario = usuario(UserRole.TECNICO, null);

            UsuarioMeOutputDto dto = mapper.toMeOutputDto(usuario, null);

            assertThat(dto.assessoria()).isNull();
        }
    }

    private Usuario usuarioTecnico() {
        return usuario(UserRole.TECNICO, assessoria());
    }

    private Usuario usuarioAtleta() {
        return usuario(UserRole.ATLETA, assessoria());
    }

    private Usuario usuario(UserRole role, Assessoria assessoria) {
        return Usuario.builder()
                .id(UUID.randomUUID())
                .nome("João")
                .sobrenome("Silva")
                .email("joao.silva@exemplo.com")
                .keycloakId(UUID.randomUUID().toString())
                .role(role)
                .assessoria(assessoria)
                .build();
    }

    private Assessoria assessoria() {
        return Assessoria.builder()
                .id(UUID.randomUUID())
                .nome("Corridas Serra")
                .dominio("corridasserra")
                .build();
    }
}
