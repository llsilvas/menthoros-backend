package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.compliance.SchemaVersion;
import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.dto.llm.v2.BlocoDto;
import br.com.menthoros.backend.dto.llm.v2.Papel;
import br.com.menthoros.backend.dto.llm.v2.PlanoSemanalLlmDtoV2;
import br.com.menthoros.backend.dto.llm.v2.TreinoPlanejadoLlmDtoV2;
import br.com.menthoros.backend.dto.llm.v2.UnidadeQuantidade;
import br.com.menthoros.backend.dto.llm.v2.Zona;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EvalAgreementGrader")
class EvalAgreementGraderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SessionResolver sessionResolver =
            new SessionResolver(new ZoneResolver(new ZonaTreinoService()), new TssCalculatorService());
    private final EvalAgreementGrader grader = new EvalAgreementGrader(objectMapper, sessionResolver);
    private final AthleteZones zonas = new AthleteZones(190, 160, BigDecimal.valueOf(5.0));

    @Nested
    @DisplayName("avaliar")
    class Avaliar {

        @Test
        @DisplayName("v1 — sem divergência quando resposta e plano final batem")
        void v1SemDivergenciaQuandoBatem() throws Exception {
            String resposta = respostaV1("CONTINUO", 40);
            String planoFinal = planoFinal("CONTINUO", 40);

            var resultado = grader.avaliar(resposta, SchemaVersion.CURRENT, zonas, planoFinal);

            assertThat(resultado.divergencias()).isEmpty();
            assertThat(resultado.totalTreinosComparados()).isEqualTo(1);
        }

        @Test
        @DisplayName("v1 — detecta divergência de tipoTreino e tssPlanejado")
        void v1DetectaDivergencia() throws Exception {
            String resposta = respostaV1("CONTINUO", 40);
            String planoFinal = planoFinal("FARTLEK", 55);

            var resultado = grader.avaliar(resposta, SchemaVersion.CURRENT, zonas, planoFinal);

            assertThat(resultado.divergencias()).hasSize(2);
            assertThat(resultado.divergencias()).extracting("campo")
                    .containsExactlyInAnyOrder("tipoTreino", "tssPlanejado");
        }

        @Test
        @DisplayName("v2 — resolve via SessionResolver antes de comparar")
        void v2ResolveAntesDeComparar() throws Exception {
            BlocoDto bloco = new BlocoDto(Papel.PRINCIPAL, 1, BigDecimal.valueOf(30), UnidadeQuantidade.MIN,
                    Zona.Z2, null);
            TreinoPlanejadoLlmDtoV2 treinoV2 = new TreinoPlanejadoLlmDtoV2("TERCA", "CONTINUO",
                    "justificativa", List.of(bloco));
            PlanoSemanalLlmDtoV2 planoV2 = PlanoSemanalLlmDtoV2.builder()
                    .volumePlanejadoKm(20.0).volumeAlvoKm(20.0).tsbInicio(5.0).tsbFim(5.0)
                    .status("PLANEJADO").objetivoSemanal("objetivo")
                    .treinosPlanejados(List.of(treinoV2)).build();
            String respostaV2 = objectMapper.writeValueAsString(planoV2);
            String planoFinal = planoFinal("CONTINUO", 40); // valor arbitrário, só prova que resolve sem lançar

            var resultado = grader.avaliar(respostaV2, SchemaVersion.V2, zonas, planoFinal);

            assertThat(resultado.totalTreinosComparados()).isEqualTo(1);
        }

        @Test
        @DisplayName("listas de tamanhos diferentes — compara só os N primeiros, reporta o resto como não comparado")
        void listasDeTamanhosDiferentes() throws Exception {
            String resposta = respostaV1("CONTINUO", 40);
            String planoFinalComDois = "[" + planoFinalTreino("CONTINUO", 40) + "," + planoFinalTreino("LONGO", 80) + "]";

            var resultado = grader.avaliar(resposta, SchemaVersion.CURRENT, zonas, planoFinalComDois);

            assertThat(resultado.totalTreinosComparados()).isEqualTo(1);
            assertThat(resultado.treinosNaRespostaNaoComparados()).isEqualTo(1);
        }

        private String respostaV1(String tipoTreino, int tss) throws Exception {
            EtapaTreinoLlmDto etapa = new EtapaTreinoLlmDto(1, "AQUECIMENTO", "aquecimento", 10, 1.0,
                    "107-121 bpm", 1, "6:00/km");
            TreinoPlanejadoLlmDto treino = new TreinoPlanejadoLlmDto("TERCA", tipoTreino, "121-133 bpm",
                    tss, 0.8, 5, "justificativa", "44:00", 7.0, "5:30/km", List.of(etapa));
            PlanoSemanalLlmDto plano = new PlanoSemanalLlmDto(20.0, 20.0, 5.0, 5.0, "PLANEJADO",
                    "objetivo", List.of(treino), List.of());
            return objectMapper.writeValueAsString(plano);
        }

        private String planoFinal(String tipoTreino, int tss) {
            return "[" + planoFinalTreino(tipoTreino, tss) + "]";
        }

        private String planoFinalTreino(String tipoTreino, int tss) {
            return "{\"dataTreino\":\"2026-09-15\",\"tipoTreino\":\"" + tipoTreino + "\",\"zonaAlvo\":null,"
                    + "\"tssPlanejado\":" + tss + ",\"justificativaIa\":\"x\",\"editadoPeloCoach\":false}";
        }
    }
}
