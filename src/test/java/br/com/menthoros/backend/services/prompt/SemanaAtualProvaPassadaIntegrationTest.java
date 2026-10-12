package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.mapper.TreinoMapperImpl;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider.ContextoTreino;
import br.com.menthoros.backend.services.plano.ProvaNoPlanoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * fix-planner-ignora-prova-na-semana, task 6.2 (CA6) — {@code SEMANA_ATUAL} com prova já passada na
 * semana: a validação não a vê (não entra em {@code effectiveDays}/{@code provasDaSemana}, porque
 * só "amanhã" em diante conta — task 1.1), mas o persister ({@code garantirProvasNaSemana}, que não
 * filtra por "hoje") continua garantindo a PROVA no plano final. As duas pontas do pipeline, juntas.
 */
@DisplayName("Integração: SEMANA_ATUAL com prova já passada (CA6)")
class SemanaAtualProvaPassadaIntegrationTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 14);           // quarta
    private static final LocalDate INICIO_SEMANA = LocalDate.of(2026, 10, 12);  // segunda (já em curso)
    private static final LocalDate FIM_SEMANA = INICIO_SEMANA.plusDays(6);
    private static final LocalDate DATA_PROVA = LocalDate.of(2026, 10, 12);     // segunda, já passou

    private final ProvaRepository provaRepository = mock(ProvaRepository.class);
    private final Atleta atleta = Atleta.builder()
            .id(UUID.randomUUID())
            .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
            .diasDisponiveis(List.of(DiaSemana.TERCA, DiaSemana.QUINTA, DiaSemana.SABADO))
            .build();

    @Test
    @DisplayName("builder: prova de segunda (já passada) não entra em provasDaSemana — a validação não a vê")
    void builderNaoVeAProvaPassada() {
        Prova provaPassada = prova();

        TreinoHistoricoProvider provider = mock(TreinoHistoricoProvider.class);
        when(provider.prepararContexto(any())).thenReturn(
                new ContextoTreino(HOJE, List.of(), List.of(provaPassada), List.of()));
        PlanoTreinoPromptBuilder builder = PlanoPromptArquetipos.builder(provider, provaRepository);

        try (MockedStatic<LocalDate> now = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            now.when(LocalDate::now).thenReturn(HOJE);
            var gerado = builder.buildOptimizedPrompt(atleta, metaDados(), null, INICIO_SEMANA,
                    List.of(DiaSemana.TERCA, DiaSemana.QUINTA, DiaSemana.SABADO), null, null, null, false);

            assertThat(gerado.cobertura().provasDaSemana()).isEmpty();
            assertThat(gerado.cobertura().effectiveDays()).doesNotContain(DiaSemana.SEGUNDA);
        }
    }

    @Test
    @DisplayName("persister: garantirProvasNaSemana insere a PROVA de segunda mesmo com a semana em curso")
    void persisterGaranteAProvaPassada() {
        Prova provaPassada = prova();
        when(provaRepository.findByAtletaAndDataProvaBetweenOrderByDataProvaAsc(atleta, INICIO_SEMANA, FIM_SEMANA))
                .thenReturn(List.of(provaPassada));
        ProvaNoPlanoService provaNoPlanoService = new ProvaNoPlanoService(
                provaRepository, new TreinoMapperImpl(null, null), null, null);

        List<TreinoPlanejadoLlmDto> validados = List.of(
                treino("TERCA"), treino("QUINTA"), treino("SABADO")); // plano já validado, sem SEGUNDA (passou)

        var resultado = provaNoPlanoService.garantirProvasNaSemana(validados, atleta, INICIO_SEMANA, FIM_SEMANA);

        assertThat(resultado).filteredOn(t -> "SEGUNDA".equals(t.diaSemana()))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.tipoTreino()).isEqualTo("PROVA");
                    assertThat(t.provaId()).isEqualTo(provaPassada.getId());
                });
        assertThat(resultado).hasSize(4); // TERCA, QUINTA, SABADO + a PROVA garantida
    }

    private static Prova prova() {
        Prova p = new Prova();
        p.setId(UUID.randomUUID());
        p.setNomeProva("Prova de Teste");
        p.setDataProva(DATA_PROVA);
        p.setDistancia(DistanciaProva.KM_10);
        p.setDistanciaKm(new BigDecimal("10.0"));
        p.setProvaAlvo(false);
        return p;
    }

    private static PlanoMetaDados metaDados() {
        return PlanoMetaDados.builder().diaPreferidoLongo(DiaSemana.SABADO).build();
    }

    private static TreinoPlanejadoLlmDto treino(String dia) {
        return new TreinoPlanejadoLlmDto(dia, "FACIL", null, 40, 0.7, 4, null, "40:00", 6.5, "6:00-6:30/km",
                List.of(new EtapaTreinoLlmDto(1, "PRINCIPAL", null, 40, 6.5, null, 1, null)));
    }
}
