package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.input.AssessoriaInputDto;
import br.com.menthoros.backend.dto.output.AssessoriaOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AssessoriaMapper - Testes unitários")
class AssessoriaMapperTest {

    private final AssessoriaMapper mapper = new AssessoriaMapper();

    @Test
    @DisplayName("toEntity mapeia nome, dominio, plano e maxAtletas")
    void toEntityMapeiaCampos() {
        AssessoriaInputDto input = new AssessoriaInputDto(
                "Corridas Serra", "corridasserra", PlanoAssessoria.PRO,
                "contato@corridasserra.com", 100, 10);

        Assessoria entity = mapper.toEntity(input);

        assertThat(entity.getNome()).isEqualTo("Corridas Serra");
        assertThat(entity.getDominio()).isEqualTo("corridasserra");
        assertThat(entity.getPlano()).isEqualTo(PlanoAssessoria.PRO);
        assertThat(entity.getMaxAtletas()).isEqualTo(100);
    }

    @Test
    @DisplayName("toEntity(null) lança IllegalArgumentException")
    void toEntityNullLancaExcecao() {
        assertThatThrownBy(() -> mapper.toEntity(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("toOutputDto mapeia id, keycloakOrganizationId e ativo")
    void toOutputDtoMapeiaCampos() {
        UUID id = UUID.randomUUID();
        Assessoria entity = Assessoria.builder()
                .id(id)
                .nome("Corridas Serra")
                .dominio("corridasserra")
                .plano(PlanoAssessoria.PRO)
                .keycloakOrganizationId("org-123")
                .maxAtletas(100)
                .maxTecnicos(10)
                .ativo(true)
                .createdAt(LocalDateTime.now())
                .build();

        AssessoriaOutputDto dto = mapper.toOutputDto(entity);

        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.keycloakOrganizationId()).isEqualTo("org-123");
        assertThat(dto.ativo()).isTrue();
    }

    @Test
    @DisplayName("toOutputDto(null) lança IllegalArgumentException")
    void toOutputDtoNullLancaExcecao() {
        assertThatThrownBy(() -> mapper.toOutputDto(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
