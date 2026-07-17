package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.input.AtletaInputDto;
import br.com.menthoros.backend.dto.output.AtletaOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.StatusVencimentoPlano;
import br.com.menthoros.backend.enums.TipoPlanoAtleta;
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
    @DisplayName("toOutputDto — dados de cobrança")
    class ToOutputDtoCobranca {

        @Test
        @DisplayName("dataVencimentoPlano nulo → tipoPlanoAtleta e statusVencimentoPlano ausentes")
        void semDadosDeCobranca() {
            Atleta atleta = atletaBase().build();

            AtletaOutputDto dto = mapper.toOutputDto(atleta);

            assertThat(dto.dataVencimentoPlano()).isNull();
            assertThat(dto.tipoPlanoAtleta()).isNull();
            assertThat(dto.statusVencimentoPlano()).isNull();
        }

        @Test
        @DisplayName("dataVencimentoPlano no passado → VENCIDO")
        void dataNoPassadoRetornaVencido() {
            Atleta atleta = atletaBase()
                    .tipoPlanoAtleta(TipoPlanoAtleta.MENSAL)
                    .dataVencimentoPlano(LocalDate.now().minusDays(5))
                    .build();

            AtletaOutputDto dto = mapper.toOutputDto(atleta);

            assertThat(dto.tipoPlanoAtleta()).isEqualTo(TipoPlanoAtleta.MENSAL);
            assertThat(dto.statusVencimentoPlano()).isEqualTo(StatusVencimentoPlano.VENCIDO);
        }

        @Test
        @DisplayName("dataVencimentoPlano dentro de 7 dias → PROXIMO_VENCIMENTO")
        void dataProximaRetornaProximoVencimento() {
            Atleta atleta = atletaBase()
                    .dataVencimentoPlano(LocalDate.now().plusDays(3))
                    .build();

            AtletaOutputDto dto = mapper.toOutputDto(atleta);

            assertThat(dto.statusVencimentoPlano()).isEqualTo(StatusVencimentoPlano.PROXIMO_VENCIMENTO);
        }

        @Test
        @DisplayName("dataVencimentoPlano fora da janela de alerta → EM_DIA")
        void dataDistanteRetornaEmDia() {
            Atleta atleta = atletaBase()
                    .dataVencimentoPlano(LocalDate.now().plusDays(30))
                    .build();

            AtletaOutputDto dto = mapper.toOutputDto(atleta);

            assertThat(dto.statusVencimentoPlano()).isEqualTo(StatusVencimentoPlano.EM_DIA);
        }
    }

    @Nested
    @DisplayName("updateEntity — dados de cobrança (PUT é full update — CLAUDE.md HTTP Semantics)")
    class UpdateEntityCobranca {

        @Test
        @DisplayName("informa só dataVencimentoPlano → tipoPlanoAtleta vira null, demais campos preservados")
        void atualizaSoData() {
            Atleta atleta = atletaBase()
                    .tipoPlanoAtleta(TipoPlanoAtleta.ANUAL)
                    .dataVencimentoPlano(LocalDate.now())
                    .build();
            LocalDate novaData = LocalDate.now().plusMonths(1);

            mapper.updateEntity(inputCom(null, novaData), atleta);

            assertThat(atleta.getDataVencimentoPlano()).isEqualTo(novaData);
            assertThat(atleta.getTipoPlanoAtleta()).isNull();
            assertThat(atleta.getNome()).isEqualTo("Atleta Teste");
            assertThat(atleta.getObjetivo()).isEqualTo("Correr 10K");
        }

        @Test
        @DisplayName("informa só tipoPlanoAtleta → dataVencimentoPlano vira null")
        void atualizaSoTipo() {
            Atleta atleta = atletaBase().build();

            mapper.updateEntity(inputCom(TipoPlanoAtleta.MENSAL, null), atleta);

            assertThat(atleta.getTipoPlanoAtleta()).isEqualTo(TipoPlanoAtleta.MENSAL);
            assertThat(atleta.getDataVencimentoPlano()).isNull();
        }

        @Test
        @DisplayName("informa os dois → ambos persistidos")
        void atualizaOsDois() {
            Atleta atleta = atletaBase().build();
            LocalDate data = LocalDate.now().plusDays(10);

            mapper.updateEntity(inputCom(TipoPlanoAtleta.TRIMESTRAL, data), atleta);

            assertThat(atleta.getTipoPlanoAtleta()).isEqualTo(TipoPlanoAtleta.TRIMESTRAL);
            assertThat(atleta.getDataVencimentoPlano()).isEqualTo(data);
        }

        @Test
        @DisplayName("não informa nenhum dos dois → ambos ficam null, sem regressão nos demais campos")
        void naoInformaNenhum() {
            Atleta atleta = atletaBase()
                    .tipoPlanoAtleta(TipoPlanoAtleta.SEMESTRAL)
                    .dataVencimentoPlano(LocalDate.now())
                    .build();

            mapper.updateEntity(inputCom(null, null), atleta);

            assertThat(atleta.getTipoPlanoAtleta()).isNull();
            assertThat(atleta.getDataVencimentoPlano()).isNull();
            assertThat(atleta.getNome()).isEqualTo("Atleta Teste");
        }

        private AtletaInputDto inputCom(TipoPlanoAtleta tipoPlanoAtleta, LocalDate dataVencimentoPlano) {
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
                    tipoPlanoAtleta,
                    dataVencimentoPlano
            );
        }
    }

    private Atleta.AtletaBuilder atletaBase() {
        return Atleta.builder()
                .id(UUID.randomUUID())
                .nome("Atleta Teste")
                .objetivo("Correr 10K");
    }
}
