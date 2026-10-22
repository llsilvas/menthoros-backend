package br.com.menthoros.backend.services.plano;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.mapper.TreinoMapperImpl;
import br.com.menthoros.backend.repository.ProvaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code provaNoDiaAnteriorAoInicio} (fix-planner-ignora-prova-na-semana, task 3.2): o D+1 de uma
 * prova no último dia da semana anterior é o primeiro dia do plano seguinte, que não a enxerga.
 */
@DisplayName("ProvaNoPlanoService.provaNoDiaAnteriorAoInicio")
class ProvaNoPlanoServiceDiaAnteriorTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 10, 19);       // segunda
    private static final LocalDate DOMINGO_ANTERIOR = INICIO.minusDays(1);    // 18/10

    private ProvaRepository provaRepository;
    private ProvaNoPlanoService service;
    private final Atleta atleta = Atleta.builder().build();

    @BeforeEach
    void setUp() {
        provaRepository = mock(ProvaRepository.class);
        service = new ProvaNoPlanoService(provaRepository, new TreinoMapperImpl(null, null), null, null);
    }

    @Test
    @DisplayName("prova no dia anterior ao início da semana → presente")
    void provaNoDiaAnterior() {
        Prova prova = prova(DOMINGO_ANTERIOR);
        when(provaRepository.findByAtletaAndDataProvaBetweenOrderByDataProvaAsc(atleta, DOMINGO_ANTERIOR, DOMINGO_ANTERIOR))
                .thenReturn(List.of(prova));

        assertThat(service.provaNoDiaAnteriorAoInicio(atleta, INICIO)).containsSame(prova);
    }

    @Test
    @DisplayName("sem prova nesse dia → vazio")
    void semProva() {
        when(provaRepository.findByAtletaAndDataProvaBetweenOrderByDataProvaAsc(atleta, DOMINGO_ANTERIOR, DOMINGO_ANTERIOR))
                .thenReturn(List.of());

        assertThat(service.provaNoDiaAnteriorAoInicio(atleta, INICIO)).isEmpty();
    }

    @Test
    @DisplayName("a consulta cobre exatamente o dia anterior ao início (uma data, nenhuma janela maior)")
    void consultaDeUmDiaSo() {
        service.provaNoDiaAnteriorAoInicio(atleta, INICIO);

        verify(provaRepository).findByAtletaAndDataProvaBetweenOrderByDataProvaAsc(atleta, DOMINGO_ANTERIOR, DOMINGO_ANTERIOR);
    }

    @Test
    @DisplayName("duas provas no mesmo dia: devolve a primeira (caso raro aceito)")
    void duasProvasNoMesmoDia() {
        Prova a = prova(DOMINGO_ANTERIOR);
        Prova b = prova(DOMINGO_ANTERIOR);
        when(provaRepository.findByAtletaAndDataProvaBetweenOrderByDataProvaAsc(atleta, DOMINGO_ANTERIOR, DOMINGO_ANTERIOR))
                .thenReturn(List.of(a, b));

        assertThat(service.provaNoDiaAnteriorAoInicio(atleta, INICIO)).containsSame(a);
    }

    private static Prova prova(LocalDate data) {
        Prova p = new Prova();
        p.setId(UUID.randomUUID());
        p.setNomeProva("Prova de " + data);
        p.setDataProva(data);
        return p;
    }
}
