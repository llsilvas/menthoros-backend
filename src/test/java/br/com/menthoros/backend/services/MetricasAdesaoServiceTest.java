package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.AdesaoDiariaDto;
import br.com.menthoros.backend.dto.output.AdesaoSemanalDto;
import br.com.menthoros.backend.dto.output.DiaAdesaoDto;
import br.com.menthoros.backend.dto.output.SemanaAdesaoDiariaDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricasAdesaoServiceTest {

    @Mock
    private AtletaRepository atletaRepository;
    @Mock
    private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock
    private TreinoRealizadoRepository treinoRealizadoRepository;

    private MetricasAdesaoService service;

    private UUID atletaId;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        service = new MetricasAdesaoService(
                atletaRepository, treinoPlanejadoRepository, treinoRealizadoRepository);
        atletaId = UUID.randomUUID();
        atleta = Atleta.builder().id(atletaId).nome("João").build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("getAdesaoSemanal")
    class GetAdesaoSemanal {

        @Test
        @DisplayName("calcula percentual da semana atual, médias das últimas 4 e dias com treino")
        void calculaAdesaoSemanal() {
            when(atletaRepository.findById(atletaId)).thenReturn(Optional.of(atleta));
            when(treinoPlanejadoRepository.countPlannedTrainings(eq(atletaId), any(), any())).thenReturn(10);
            when(treinoRealizadoRepository.countRealizedTrainings(eq(atletaId), any(), any())).thenReturn(5);
            when(treinoRealizadoRepository.findRealizedTrainingsByWeek(eq(atletaId), any(), any()))
                    .thenReturn(List.of(
                            treinoRealizado(LocalDate.of(2026, 5, 4)),   // segunda
                            treinoRealizado(LocalDate.of(2026, 5, 6)),   // quarta
                            treinoRealizado(LocalDate.of(2026, 5, 6)))); // quarta (repetida → 2 dias distintos)

            AdesaoSemanalDto dto = service.getAdesaoSemanal(atletaId.toString());

            assertEquals(atletaId.toString(), dto.atletaId());
            assertEquals("João", dto.nomeAtleta());
            assertEquals(50.0, dto.semanaAtual().percentualRealizacao());   // 5/10
            assertEquals(2, dto.semanaAtual().diasComTreino());              // 2 dias distintos
            assertEquals(4, dto.ultimas4Semanas().size());
            assertEquals(50.0, dto.mediaUltimas4Semanas());
        }

        @Test
        @DisplayName("retorna 0% quando não há treinos planejados")
        void semPlanejados() {
            when(atletaRepository.findById(atletaId)).thenReturn(Optional.of(atleta));
            when(treinoPlanejadoRepository.countPlannedTrainings(eq(atletaId), any(), any())).thenReturn(0);
            when(treinoRealizadoRepository.countRealizedTrainings(eq(atletaId), any(), any())).thenReturn(0);
            when(treinoRealizadoRepository.findRealizedTrainingsByWeek(eq(atletaId), any(), any()))
                    .thenReturn(List.of());

            AdesaoSemanalDto dto = service.getAdesaoSemanal(atletaId.toString());

            assertEquals(0.0, dto.semanaAtual().percentualRealizacao());
            assertEquals(0, dto.semanaAtual().diasComTreino());
            assertEquals(0.0, dto.mediaUltimas4Semanas());
        }

        @Test
        @DisplayName("lança exceção quando o atleta não existe")
        void atletaInexistente() {
            when(atletaRepository.findById(atletaId)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> service.getAdesaoSemanal(atletaId.toString()));
        }
    }

    @Nested
    @DisplayName("getAdesaoDiaria")
    class GetAdesaoDiaria {

        @Test
        @DisplayName("agrupa em 5 semanas de 7 dias e calcula 100% no dia com treino planejado e realizado")
        void agrupaPorSemanaEDia() {
            when(atletaRepository.findById(atletaId)).thenReturn(Optional.of(atleta));
            LocalDate hoje = LocalDate.now();
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(eq(atletaId), any(), any()))
                    .thenReturn(List.of(treinoPlanejado(hoje)));
            when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoBetween(eq(atletaId), any(), any()))
                    .thenReturn(List.of(treinoRealizado(hoje)));

            AdesaoDiariaDto dto = service.getAdesaoDiaria(atletaId.toString());

            assertEquals(5, dto.semanas().size());
            dto.semanas().forEach(s -> assertEquals(7, s.dias().size()));

            DiaAdesaoDto diaHoje = encontrarDia(dto, hoje);
            assertNotNull(diaHoje);
            assertEquals(1, diaHoje.treinosPlanejados());
            assertEquals(1, diaHoje.treinosRealizados());
            assertEquals(100.0, diaHoje.percentual());
        }

        @Test
        @DisplayName("retorna 0% em todos os dias quando não há treinos")
        void semTreinos() {
            when(atletaRepository.findById(atletaId)).thenReturn(Optional.of(atleta));
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(eq(atletaId), any(), any()))
                    .thenReturn(List.of());
            when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoBetween(eq(atletaId), any(), any()))
                    .thenReturn(List.of());

            AdesaoDiariaDto dto = service.getAdesaoDiaria(atletaId.toString());

            assertEquals(5, dto.semanas().size());
            boolean todosZero = dto.semanas().stream()
                    .flatMap(s -> s.dias().stream())
                    .allMatch(d -> d.percentual() == 0.0);
            assertTrue(todosZero);
        }

        @Test
        @DisplayName("mapeia o rótulo do dia da semana corretamente (domingo)")
        void mapeiaDiaSemana() {
            when(atletaRepository.findById(atletaId)).thenReturn(Optional.of(atleta));
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(eq(atletaId), any(), any()))
                    .thenReturn(List.of());
            when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoBetween(eq(atletaId), any(), any()))
                    .thenReturn(List.of());

            AdesaoDiariaDto dto = service.getAdesaoDiaria(atletaId.toString());

            // Semana inicia no domingo (WeekFields ENGLISH) → primeiro dia rotulado "DOM"
            SemanaAdesaoDiariaDto primeira = dto.semanas().get(0);
            DiaAdesaoDto primeiroDia = primeira.dias().get(0);
            LocalDate dataPrimeiroDia = LocalDate.parse(primeiroDia.data());
            assertEquals(DayOfWeek.SUNDAY, dataPrimeiroDia.getDayOfWeek());
            assertEquals("DOM", primeiroDia.diaSemana());
        }
    }

    @Nested
    @DisplayName("getAdesaoDiariaAssessoria")
    class GetAdesaoDiariaAssessoria {

        @Test
        @DisplayName("calcula a média de adesão dos atletas do tenant")
        void calculaMediaDoTenant() {
            UUID tenantId = UUID.randomUUID();
            TenantContext.setTenantId(tenantId);
            when(atletaRepository.findAllByTenantIdOrderByNome(tenantId)).thenReturn(List.of(atleta));
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(any(), any(), any()))
                    .thenReturn(List.of(treinoPlanejado(LocalDate.now())));
            when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoBetween(any(), any(), any()))
                    .thenReturn(List.of(treinoRealizado(LocalDate.now())));

            AdesaoDiariaDto dto = service.getAdesaoDiariaAssessoria();

            assertEquals(tenantId.toString(), dto.atletaId());
            assertEquals("Assessoria", dto.nomeAtleta());
            assertEquals(5, dto.semanas().size());
            // Cada atleta tem planejado=realizado em todos os dias → 100% por dia e na semana
            assertEquals(100.0, dto.semanas().get(0).percentualGeral());
        }

        @Test
        @DisplayName("retorna estrutura vazia quando o tenant não tem atletas")
        void tenantSemAtletas() {
            UUID tenantId = UUID.randomUUID();
            TenantContext.setTenantId(tenantId);
            when(atletaRepository.findAllByTenantIdOrderByNome(tenantId)).thenReturn(List.of());

            AdesaoDiariaDto dto = service.getAdesaoDiariaAssessoria();

            assertEquals(tenantId.toString(), dto.atletaId());
            assertEquals("Assessoria", dto.nomeAtleta());
            assertTrue(dto.semanas().isEmpty());
        }

        @Test
        @DisplayName("lança exceção quando não há tenant no contexto")
        void semTenant() {
            assertThrows(RuntimeException.class, () -> service.getAdesaoDiariaAssessoria());
        }
    }

    // --- helpers ---

    private DiaAdesaoDto encontrarDia(AdesaoDiariaDto dto, LocalDate data) {
        return dto.semanas().stream()
                .flatMap(s -> s.dias().stream())
                .filter(d -> d.data().equals(data.toString()))
                .findFirst()
                .orElse(null);
    }

    private TreinoPlanejado treinoPlanejado(LocalDate data) {
        TreinoPlanejado tp = new TreinoPlanejado();
        tp.setDataTreino(data);
        return tp;
    }

    private TreinoRealizado treinoRealizado(LocalDate data) {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setDataTreino(data);
        return tr;
    }
}
