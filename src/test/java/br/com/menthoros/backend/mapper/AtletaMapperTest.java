package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.domain.billing.AthleteBilling;
import br.com.menthoros.backend.dto.input.AtletaInputDto;
import br.com.menthoros.backend.dto.output.AtletaOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.AthleteBillingStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.Sexo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AtletaMapperTest {

    private final AtletaMapper mapper = new AtletaMapperImpl(null);

    @Nested
    @DisplayName("toOutputDto — cobrança")
    class ToOutputDtoCobranca {

        @Test
        @DisplayName("o mapper não resolve cobrança: billingStatus e nextDueDate saem nulos")
        void mapperNaoResolveCobranca() {
            AtletaOutputDto dto = mapper.toOutputDto(atletaBase().build());

            assertThat(dto.billingStatus()).isNull();
            assertThat(dto.nextDueDate()).isNull();
        }

        @Test
        @DisplayName("withBilling preenche os dois campos e preserva o resto")
        void withBillingPreenche() {
            AtletaOutputDto dto = mapper.toOutputDto(atletaBase().email("a@b.com").build());

            AtletaOutputDto comCobranca = dto.withBilling(
                    new AthleteBilling(AthleteBillingStatus.DUE_SOON, LocalDate.of(2026, 10, 10)));

            assertThat(comCobranca.billingStatus()).isEqualTo(AthleteBillingStatus.DUE_SOON);
            assertThat(comCobranca.nextDueDate()).isEqualTo(LocalDate.of(2026, 10, 10));
            assertThat(comCobranca.nome()).isEqualTo("Atleta Teste");
            assertThat(comCobranca.email()).isEqualTo("a@b.com");
        }

        @Test
        @DisplayName("withBilling(null) mantém os campos ausentes (atleta sem contrato)")
        void withBillingNulo() {
            AtletaOutputDto dto = mapper.toOutputDto(atletaBase().build()).withBilling(null);

            assertThat(dto.billingStatus()).isNull();
            assertThat(dto.nextDueDate()).isNull();
        }
    }

    @Nested
    @DisplayName("dados pessoais — email e sexo (bug: front envia MASCULINO, banco aceitava só M/F/O)")
    class DadosPessoais {

        @Test
        @DisplayName("updateEntity copia sexo como enum e email para a entidade")
        void updateEntityCopiaSexoEEmail() {
            Atleta atleta = atletaBase().sexo(Sexo.MASCULINO).email("antigo@teste.com").build();

            mapper.updateEntity(inputCom("teste@teste.com", Sexo.FEMININO), atleta);

            assertThat(atleta.getSexo()).isEqualTo(Sexo.FEMININO);
            assertThat(atleta.getEmail()).isEqualTo("teste@teste.com");
        }

        @Test
        @DisplayName("toOutputDto expõe sexo e email — sem isso o front reenvia o default a cada edição")
        void toOutputDtoExpoeSexoEEmail() {
            Atleta atleta = atletaBase().sexo(Sexo.OUTRO).email("atleta@teste.com").build();

            AtletaOutputDto dto = mapper.toOutputDto(atleta);

            assertThat(dto.sexo()).isEqualTo(Sexo.OUTRO);
            assertThat(dto.email()).isEqualTo("atleta@teste.com");
        }

        @Test
        @DisplayName("sexo e email nulos no input → ficam nulos (atleta legado sem cadastro)")
        void aceitaNulos() {
            Atleta atleta = atletaBase().sexo(Sexo.MASCULINO).email("antigo@teste.com").build();

            mapper.updateEntity(inputCom(null, null), atleta);

            assertThat(atleta.getSexo()).isNull();
            assertThat(atleta.getEmail()).isNull();
        }

        @Test
        @DisplayName("updateEntity preserva nome e objetivo (PUT é full update dos campos do input)")
        void preservaDemaisCampos() {
            Atleta atleta = atletaBase().build();

            mapper.updateEntity(inputCom("teste@teste.com", Sexo.FEMININO), atleta);

            assertThat(atleta.getNome()).isEqualTo("Atleta Teste");
            assertThat(atleta.getObjetivo()).isEqualTo("Correr 10K");
        }
    }

    private AtletaInputDto inputCom(String email, Sexo sexo) {
        return new AtletaInputDto(
                "Atleta Teste",
                null,
                BigDecimal.valueOf(70),
                BigDecimal.valueOf(175),
                "Correr 10K",
                NivelExperiencia.INTERMEDIARIO,
                Set.of(DiaSemana.SEGUNDA),
                null,
                false,
                null,
                email,
                sexo
        );
    }

    private Atleta.AtletaBuilder atletaBase() {
        return Atleta.builder()
                .id(UUID.randomUUID())
                .nome("Atleta Teste")
                .objetivo("Correr 10K");
    }
}
