package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.RevisaoSemanal;
import br.com.menthoros.backend.enums.FocusSource;
import br.com.menthoros.backend.enums.NivelAderencia;
import br.com.menthoros.backend.enums.RecommendationType;
import br.com.menthoros.backend.repository.RevisaoSemanalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Resolução da revisão consumível: flag de injeção como kill-switch e janela de validade (D11).
 * Ponto único usado tanto pelo prompt quanto pela gravação do vínculo no plano — o que os dois
 * enxergam como "houve consumo" nasce daqui.
 */
@ExtendWith(MockitoExtension.class)
class WeeklyReviewPromptProviderTest {

    @Mock
    private RevisaoSemanalRepository revisaoSemanalRepository;

    private WeeklyReviewPromptProvider provider;

    private final UUID atletaId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final LocalDate semanaInicioPlano = LocalDate.of(2026, 7, 20);

    @BeforeEach
    void setUp() {
        provider = new WeeklyReviewPromptProvider(revisaoSemanalRepository);
        ReflectionTestUtils.setField(provider, "injecaoHabilitada", true);
    }

    @Nested
    @DisplayName("resolverParaGeracao")
    class ResolverParaGeracao {

        @Test
        @DisplayName("consome a revisão da semana imediatamente anterior")
        void consomeRevisaoRecente() {
            when(revisaoSemanalRepository.findRecentesByAtletaAndTenant(eq(atletaId), eq(tenantId), any(Pageable.class)))
                    .thenReturn(List.of(revisaoComFim(LocalDate.of(2026, 7, 19))));

            assertThat(provider.resolverParaGeracao(atletaId, tenantId, semanaInicioPlano)).isPresent();
        }

        @Test
        @DisplayName("não consome revisão obsoleta — atleta três semanas sem plano (D11)")
        void naoConsomeRevisaoObsoleta() {
            when(revisaoSemanalRepository.findRecentesByAtletaAndTenant(eq(atletaId), eq(tenantId), any(Pageable.class)))
                    .thenReturn(List.of(revisaoComFim(LocalDate.of(2026, 6, 28))));

            assertThat(provider.resolverParaGeracao(atletaId, tenantId, semanaInicioPlano)).isEmpty();
        }

        @Test
        @DisplayName("atleta sem nenhuma revisão não consome")
        void semRevisao() {
            when(revisaoSemanalRepository.findRecentesByAtletaAndTenant(eq(atletaId), eq(tenantId), any(Pageable.class)))
                    .thenReturn(List.of());

            assertThat(provider.resolverParaGeracao(atletaId, tenantId, semanaInicioPlano)).isEmpty();
        }

        @Test
        @DisplayName("flag de injeção desligada nem consulta o repositório")
        void flagDesligada() {
            ReflectionTestUtils.setField(provider, "injecaoHabilitada", false);

            assertThat(provider.resolverParaGeracao(atletaId, tenantId, semanaInicioPlano)).isEmpty();
            verifyNoInteractions(revisaoSemanalRepository);
        }

        @Test
        @DisplayName("tenant nulo não consulta o repositório — guarda de isolamento")
        void tenantNulo() {
            assertThat(provider.resolverParaGeracao(atletaId, null, semanaInicioPlano)).isEmpty();
            verifyNoInteractions(revisaoSemanalRepository);
        }
    }

    private RevisaoSemanal revisaoComFim(LocalDate semanaFim) {
        PlanoSemanal plano = new PlanoSemanal();
        plano.setId(UUID.randomUUID());
        plano.setSemanaInicio(semanaFim.minusDays(6));
        plano.setSemanaFim(semanaFim);
        plano.setTsbFim(new BigDecimal("-5"));
        return RevisaoSemanal.builder()
                .id(UUID.randomUUID())
                .planoSemanal(plano)
                .recommendationType(RecommendationType.MAINTAIN)
                .adherenceStatus(NivelAderencia.MEDIA)
                .completionRate(new BigDecimal("70.00"))
                .sufficientData(true)
                .nextWeekFocus("Prioridade: manter a carga atual.")
                .focusSource(FocusSource.TEMPLATE)
                .geradaEm(Instant.now())
                .build();
    }
}
