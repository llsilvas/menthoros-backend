package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.ActivityTypeCompatibilityMatrix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateSelectorTest {

    @Mock
    private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock
    private ActivityTypeCompatibilityMatrix activityTypeCompatibilityMatrix;

    private CandidateSelector selector;
    private UUID tenantId;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        selector = new CandidateSelector(treinoPlanejadoRepository, activityTypeCompatibilityMatrix);
        tenantId = UUID.randomUUID();
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        atleta = new Atleta();
        atleta.setId(UUID.randomUUID());
        atleta.setAssessoria(assessoria);
    }

    @Nested
    @DisplayName("buscarCandidatos")
    class BuscarCandidatos {

        @Test
        @DisplayName("busca na janela [data-1, data+1] a partir da data do treino realizado (mesma janela do scheduler)")
        void buscaNaJanelaDMenos1MaisMenos1() {
            TreinoRealizado activity = activity(LocalDate.of(2026, 7, 16));
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(any(), any(), any())).thenReturn(List.of());

            selector.buscarCandidatos(activity, tenantId);

            verify(treinoPlanejadoRepository).findByAtletaIdAndDataBetween(
                    atleta.getId(), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 17));
        }

        @Test
        @DisplayName("filtra por compatibilidade de tipo ANTES de retornar (mesmo pré-filtro do scheduler)")
        void filtraPorCompatibilidade() {
            TreinoRealizado activity = activity(LocalDate.of(2026, 7, 16));
            activity.setTipoTreino(TipoTreino.FACIL);
            TreinoPlanejado compativel = planejado(TipoTreino.FACIL);
            TreinoPlanejado incompativel = planejado(TipoTreino.LONGO);
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(any(), any(), any()))
                    .thenReturn(List.of(compativel, incompativel));
            when(activityTypeCompatibilityMatrix.isCompatible(TipoTreino.FACIL, TipoTreino.FACIL)).thenReturn(true);
            when(activityTypeCompatibilityMatrix.isCompatible(TipoTreino.FACIL, TipoTreino.LONGO)).thenReturn(false);

            List<TreinoPlanejado> resultado = selector.buscarCandidatos(activity, tenantId);

            assertThat(resultado).containsExactly(compativel);
        }

        @Test
        @DisplayName("filtra candidato de outro tenant (segurança multi-tenant, mesmo comportamento do scheduler)")
        void filtraCandidatoDeOutroTenant() {
            TreinoRealizado activity = activity(LocalDate.of(2026, 7, 16));
            TreinoPlanejado candidatoOutroTenant = planejadoDeOutroTenant();
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(any(), any(), any()))
                    .thenReturn(List.of(candidatoOutroTenant));
            when(activityTypeCompatibilityMatrix.isCompatible(any(), any())).thenReturn(true);

            List<TreinoPlanejado> resultado = selector.buscarCandidatos(activity, tenantId);

            assertThat(resultado).isEmpty();
        }

        @Test
        @DisplayName("sem candidatos na janela retorna lista vazia, sem chamar o matcher de compatibilidade")
        void semCandidatosRetornaListaVazia() {
            TreinoRealizado activity = activity(LocalDate.of(2026, 7, 16));
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(any(), any(), any())).thenReturn(List.of());

            List<TreinoPlanejado> resultado = selector.buscarCandidatos(activity, tenantId);

            assertThat(resultado).isEmpty();
            verify(activityTypeCompatibilityMatrix, never()).isCompatible(any(), any());
        }
    }

    private TreinoRealizado activity(LocalDate data) {
        TreinoRealizado t = new TreinoRealizado();
        t.setId(UUID.randomUUID());
        t.setAtleta(atleta);
        t.setDataTreino(data);
        return t;
    }

    private TreinoPlanejado planejado(TipoTreino tipo) {
        TreinoPlanejado p = new TreinoPlanejado();
        p.setId(UUID.randomUUID());
        p.setAtleta(atleta);
        p.setTipoTreino(tipo);
        return p;
    }

    private TreinoPlanejado planejadoDeOutroTenant() {
        Assessoria outraAssessoria = new Assessoria();
        outraAssessoria.setId(UUID.randomUUID());
        Atleta outroAtleta = new Atleta();
        outroAtleta.setId(UUID.randomUUID());
        outroAtleta.setAssessoria(outraAssessoria);

        TreinoPlanejado p = new TreinoPlanejado();
        p.setId(UUID.randomUUID());
        p.setAtleta(outroAtleta);
        return p;
    }
}
