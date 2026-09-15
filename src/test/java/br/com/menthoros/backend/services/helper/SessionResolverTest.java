package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.dto.llm.v2.BlocoDto;
import br.com.menthoros.backend.dto.llm.v2.Papel;
import br.com.menthoros.backend.dto.llm.v2.PlanoSemanalLlmDtoV2;
import br.com.menthoros.backend.dto.llm.v2.RecuperacaoDto;
import br.com.menthoros.backend.dto.llm.v2.TreinoPlanejadoLlmDtoV2;
import br.com.menthoros.backend.dto.llm.v2.UnidadeQuantidade;
import br.com.menthoros.backend.dto.llm.v2.Zona;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SessionResolver")
class SessionResolverTest {

    private static final AthleteZones ZONAS = new AthleteZones(190, 160, BigDecimal.valueOf(4.50));

    private SessionResolver resolver;

    @BeforeEach
    void setUp() {
        ZonaTreinoService zonaTreinoService = new ZonaTreinoService();
        ZoneResolver zoneResolver = new ZoneResolver(zonaTreinoService);
        resolver = new SessionResolver(zoneResolver, new TssCalculatorService());
    }

    @Nested
    @DisplayName("resolverPlano")
    class ResolverPlano {

        @Test
        @DisplayName("bloco com repeticoes=1 não expande — 1 etapa")
        void repeticoesUm_naoExpande() {
            BlocoDto bloco = new BlocoDto(Papel.PRINCIPAL, 1, BigDecimal.valueOf(30), UnidadeQuantidade.MIN, Zona.Z2, null);
            TreinoPlanejadoLlmDto treino = resolverUmTreino(bloco);

            assertThat(treino.etapas()).hasSize(1);
            assertThat(treino.etapas().get(0).tipoEtapa()).isEqualTo("PRINCIPAL");
        }

        @Test
        @DisplayName("bloco PRINCIPAL com repeticoes=6 e recuperacao — 12 etapas, INTERVALADO+RECUPERACAO alternados")
        void repeticoesSeisComRecuperacao_dozeEtapas() {
            BlocoDto bloco = new BlocoDto(Papel.PRINCIPAL, 6, BigDecimal.valueOf(400), UnidadeQuantidade.M, Zona.Z5,
                    new RecuperacaoDto(BigDecimal.valueOf(90), UnidadeQuantidade.SEG));
            TreinoPlanejadoLlmDto treino = resolverUmTreino(bloco);

            assertThat(treino.etapas()).hasSize(12);
            for (int i = 0; i < 12; i += 2) {
                assertThat(treino.etapas().get(i).tipoEtapa()).isEqualTo("INTERVALADO");
                assertThat(treino.etapas().get(i + 1).tipoEtapa()).isEqualTo("RECUPERACAO");
            }
            // A 6ª (última) repetição também tem recuperação — Decisão 5 do design, herdada de v1.
            assertThat(treino.etapas().get(11).tipoEtapa()).isEqualTo("RECUPERACAO");
        }

        @Test
        @DisplayName("bloco PRINCIPAL com repeticoes=6 sem recuperacao — 6 etapas, só INTERVALADO")
        void repeticoesSeisSemRecuperacao_seisEtapas() {
            BlocoDto bloco = new BlocoDto(Papel.PRINCIPAL, 6, BigDecimal.valueOf(2), UnidadeQuantidade.MIN, Zona.Z4, null);
            TreinoPlanejadoLlmDto treino = resolverUmTreino(bloco);

            assertThat(treino.etapas()).hasSize(6);
            assertThat(treino.etapas()).allMatch(e -> "INTERVALADO".equals(e.tipoEtapa()));
        }

        @Test
        @DisplayName("papeis AQUEC/DESAQ/RECUP resolvem para os tipoEtapa esperados")
        void papeisResolvemParaTipoEtapaEsperado() {
            assertThat(resolverUmTreino(new BlocoDto(Papel.AQUEC, 1, BigDecimal.valueOf(10), UnidadeQuantidade.MIN, Zona.Z1, null))
                    .etapas().get(0).tipoEtapa()).isEqualTo("AQUECIMENTO");
            assertThat(resolverUmTreino(new BlocoDto(Papel.DESAQ, 1, BigDecimal.valueOf(10), UnidadeQuantidade.MIN, Zona.Z1, null))
                    .etapas().get(0).tipoEtapa()).isEqualTo("DESAQUECIMENTO");
            assertThat(resolverUmTreino(new BlocoDto(Papel.RECUP, 1, BigDecimal.valueOf(5), UnidadeQuantidade.MIN, Zona.Z1, null))
                    .etapas().get(0).tipoEtapa()).isEqualTo("RECUPERACAO");
        }

        @ParameterizedTest
        @org.junit.jupiter.params.provider.CsvSource({
                "MIN, 10",   // 10 min contínuos
                "SEG, 90",   // 90 seg (tiro curto)
                "KM, 5",     // 5 km contínuos
                "M, 400"     // 400 m (tiro)
        })
        @DisplayName("cada unidade resolve duração e distância sem lançar, com quantidade realista")
        void cadaUnidadeResolveSemLancar(UnidadeQuantidade unidade, BigDecimal quantidade) {
            BlocoDto bloco = new BlocoDto(Papel.PRINCIPAL, 1, quantidade, unidade, Zona.Z3, null);
            TreinoPlanejadoLlmDto treino = resolverUmTreino(bloco);

            EtapaTreinoLlmDto etapa = treino.etapas().get(0);
            assertThat(etapa.duracaoMin()).isPositive();
            assertThat(etapa.distanciaKm()).isPositive();
        }

        @ParameterizedTest
        @EnumSource(Zona.class)
        @DisplayName("cada zona, incluindo LIMIAR, resolve FC/pace sem lançar")
        void cadaZonaResolveSemLancar(Zona zona) {
            BlocoDto bloco = new BlocoDto(Papel.PRINCIPAL, 1, BigDecimal.valueOf(20), UnidadeQuantidade.MIN, zona, null);
            TreinoPlanejadoLlmDto treino = resolverUmTreino(bloco);

            EtapaTreinoLlmDto etapa = treino.etapas().get(0);
            assertThat(etapa.fcAlvoEtapa()).matches("\\d+-\\d+ bpm");
            assertThat(etapa.ritmoAlvo()).matches("\\d+:\\d{2}-\\d+:\\d{2}/km");
        }

        @Test
        @DisplayName("repeticoes insuficientes para a estrutura mínima não lança — só produz menos etapas")
        void repeticoesInsuficientesNaoLanca() {
            BlocoDto blocoAquec = new BlocoDto(Papel.AQUEC, 1, BigDecimal.valueOf(10), UnidadeQuantidade.MIN, Zona.Z1, null);
            BlocoDto blocoPrincipal = new BlocoDto(Papel.PRINCIPAL, 1, BigDecimal.valueOf(200), UnidadeQuantidade.M, Zona.Z5, null);
            BlocoDto blocoDesaq = new BlocoDto(Papel.DESAQ, 1, BigDecimal.valueOf(10), UnidadeQuantidade.MIN, Zona.Z1, null);

            TreinoPlanejadoLlmDtoV2 treinoV2 = new TreinoPlanejadoLlmDtoV2("SEGUNDA", "INTERVALADO", "justificativa",
                    List.of(blocoAquec, blocoPrincipal, blocoDesaq));
            PlanoSemanalLlmDtoV2 planoV2 = umPlanoCom(treinoV2);

            PlanoSemanalLlmDto resolvido = resolver.resolverPlano(planoV2, ZONAS);

            assertThat(resolvido.treinosPlanejados().get(0).etapas()).hasSize(3); // menos que o mínimo de 6
        }

        @Test
        @DisplayName("campos de nível-treino: duracaoMin, distanciaKm, fcAlvo, ritmoAlvo, tssPlanejado, intensidadePlanejada, percepcaoEsforcoEsperada")
        void camposDeNivelTreinoCalculadosCorretamente() {
            BlocoDto blocoAquec = new BlocoDto(Papel.AQUEC, 1, BigDecimal.valueOf(10), UnidadeQuantidade.MIN, Zona.Z1, null);
            BlocoDto blocoPrincipal = new BlocoDto(Papel.PRINCIPAL, 1, BigDecimal.valueOf(20), UnidadeQuantidade.MIN, Zona.Z3, null);
            BlocoDto blocoDesaq = new BlocoDto(Papel.DESAQ, 1, BigDecimal.valueOf(10), UnidadeQuantidade.MIN, Zona.Z1, null);

            TreinoPlanejadoLlmDtoV2 treinoV2 = new TreinoPlanejadoLlmDtoV2("SEGUNDA", "CONTINUO", "corrida contínua",
                    List.of(blocoAquec, blocoPrincipal, blocoDesaq));
            PlanoSemanalLlmDtoV2 planoV2 = umPlanoCom(treinoV2);

            TreinoPlanejadoLlmDto treino = resolver.resolverPlano(planoV2, ZONAS).treinosPlanejados().get(0);

            assertThat(treino.duracaoMin()).isEqualTo("40:00"); // 10+20+10 min
            assertThat(treino.distanciaKm()).isGreaterThan(0);
            assertThat(treino.fcAlvo()).matches("\\d+-\\d+ bpm");
            assertThat(treino.ritmoAlvo()).matches("\\d+:\\d{2}-\\d+:\\d{2}/km");
            assertThat(treino.tssPlanejado()).isNotNull().isPositive();
            assertThat(treino.intensidadePlanejada()).isNotNull().isPositive();
            // RPE dominante = zona mais intensa entre os blocos (Z3=6 > Z1=2)
            assertThat(treino.percepcaoEsforcoEsperada()).isEqualTo(6);
            assertThat(treino.justificativaIa()).isEqualTo("corrida contínua");
        }

        @Test
        @DisplayName("plano com múltiplos treinos resolve todos, preservando nível-plano")
        void planoComMultiplosTreinosResolveTodos() {
            BlocoDto bloco = new BlocoDto(Papel.PRINCIPAL, 1, BigDecimal.valueOf(30), UnidadeQuantidade.MIN, Zona.Z2, null);
            TreinoPlanejadoLlmDtoV2 treino1 = new TreinoPlanejadoLlmDtoV2("SEGUNDA", "REGENERATIVO", "j1", List.of(bloco));
            TreinoPlanejadoLlmDtoV2 treino2 = new TreinoPlanejadoLlmDtoV2("QUARTA", "REGENERATIVO", "j2", List.of(bloco));

            PlanoSemanalLlmDtoV2 planoV2 = PlanoSemanalLlmDtoV2.builder()
                    .volumePlanejadoKm(50.0)
                    .volumeAlvoKm(50.0)
                    .status("PLANEJADO")
                    .objetivoSemanal("base")
                    .treinosPlanejados(List.of(treino1, treino2))
                    .build();

            PlanoSemanalLlmDto resolvido = resolver.resolverPlano(planoV2, ZONAS);

            assertThat(resolvido.treinosPlanejados()).hasSize(2);
            assertThat(resolvido.volumePlanejadoKm()).isEqualTo(50.0);
            assertThat(resolvido.objetivoSemanal()).isEqualTo("base");
        }

        private TreinoPlanejadoLlmDto resolverUmTreino(BlocoDto bloco) {
            TreinoPlanejadoLlmDtoV2 treinoV2 = new TreinoPlanejadoLlmDtoV2("SEGUNDA", "INTERVALADO", "j",
                    List.of(bloco));
            PlanoSemanalLlmDtoV2 planoV2 = umPlanoCom(treinoV2);
            return resolver.resolverPlano(planoV2, ZONAS).treinosPlanejados().get(0);
        }

        private PlanoSemanalLlmDtoV2 umPlanoCom(TreinoPlanejadoLlmDtoV2 treinoV2) {
            return PlanoSemanalLlmDtoV2.builder()
                    .volumePlanejadoKm(30.0)
                    .volumeAlvoKm(30.0)
                    .status("PLANEJADO")
                    .objetivoSemanal("teste")
                    .treinosPlanejados(List.of(treinoV2))
                    .build();
        }
    }
}
