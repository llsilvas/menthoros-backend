package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FasePeriodizacao;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.skills.eligibility.IntervaladoElegibilidadeSkill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes semânticos para IntervaladoElegibilidadeService.
 *
 * <p>Verifica que o gate fisiológico usa {@code tsbProntidaoAtual} (pré-treino)
 * e NÃO {@code tsbAtual} (pós-carga) para decisões de elegibilidade.</p>
 *
 * <p>TDD: testes escritos em RED antes da implementação.</p>
 */
class IntervaladoElegibilidadeSemanticaTest {

    private IntervaladoElegibilidadeService service;

    private static final LocalDate DATA_REFERENCIA = LocalDate.of(2026, 6, 2);

    @BeforeEach
    void setUp() {
        // Injeta a skill real — ela é stateless e determinística, não precisa de mock
        service = new IntervaladoElegibilidadeService(
                new IntervaladoElegibilidadeSkill(), new br.com.menthoros.backend.config.core.ReadinessProperties());
    }

    private Atleta atletaSaudavel(NivelExperiencia nivel) {
        return Atleta.builder()
                .nome("Atleta Semantica Test")
                .objetivo("Melhorar condicionamento")
                .nivelExperiencia(nivel)
                .temLesao(false)
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .build();
    }

    @Test
    @DisplayName("gate fisiológico usa tsbProntidaoAtual, não tsbAtual (pós-carga) — Portão 1 bloqueio absoluto")
    void gate_portao1_usaTsbProntidaoAtual_naoTsbAtual_bloqueioAbsoluto() {
        // tsbAtual (pós-carga) = -35 → bloquearia via portão 1 (TSB < -30)
        // tsbProntidaoAtual (pré-treino) = +3 → deve permitir (acima do bloqueio absoluto)
        PlanoMetaDados meta = PlanoMetaDados.builder()
                .tsbAtual(-35.0)
                .tsbProntidaoAtual(3.0)
                .ctlAtual(40.0)
                .atlAtual(20.0)
                .rampRateAtual(5.0)
                .alertaDiasConsecutivos(false)
                .diasConsecutivosTreino(2)
                .fasePeriodizacao(FasePeriodizacao.BUILD)
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .build();

        Atleta atleta = atletaSaudavel(NivelExperiencia.AVANCADO);

        // Com a correção, o gate do portão 1 deve usar tsbProntidaoAtual (+3)
        // → treino deve ser elegível (não bloqueado pelo TSB absoluto)
        RecomendacaoIntervalado resultado = service.avaliar(atleta, meta, List.of(), DATA_REFERENCIA);

        assertInstanceOf(RecomendacaoIntervalado.Elegivel.class, resultado,
                "Gate deve usar prontidão pré-treino (+3), não fadiga pós-carga (-35)");
    }

    @Test
    @DisplayName("gate fisiológico usa tsbProntidaoAtual, não tsbAtual — Portão 2 limiar por nível")
    void gate_portao2_usaTsbProntidaoAtual_naoTsbAtual_limiarNivel() {
        // Para INTERMEDIARIO, limiar do portão 2 = -15
        // tsbAtual (pós-carga) = -20 → bloquearia (abaixo de -15)
        // tsbProntidaoAtual (pré-treino) = -5 → deve passar (acima de -15)
        PlanoMetaDados meta = PlanoMetaDados.builder()
                .tsbAtual(-20.0)
                .tsbProntidaoAtual(-5.0)
                .ctlAtual(30.0)
                .atlAtual(20.0)
                .rampRateAtual(5.0)
                .alertaDiasConsecutivos(false)
                .diasConsecutivosTreino(2)
                .fasePeriodizacao(FasePeriodizacao.BUILD)
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .build();

        Atleta atleta = atletaSaudavel(NivelExperiencia.INTERMEDIARIO);

        RecomendacaoIntervalado resultado = service.avaliar(atleta, meta, List.of(), DATA_REFERENCIA);

        assertInstanceOf(RecomendacaoIntervalado.Elegivel.class, resultado,
                "Gate do portão 2 deve usar prontidão pré-treino (-5), não fadiga pós-carga (-20)");
    }

    @Test
    @DisplayName("gate bloqueia corretamente quando tsbProntidaoAtual está abaixo do limiar absoluto")
    void gate_bloqueia_quando_tsbProntidaoAtual_abaixo_limiarAbsoluto() {
        // tsbProntidaoAtual (pré-treino) = -35 → deve bloquear (bloqueio absoluto)
        // tsbAtual (pós-carga) = +5 → sem correção, não bloquearia
        PlanoMetaDados meta = PlanoMetaDados.builder()
                .tsbAtual(5.0)
                .tsbProntidaoAtual(-35.0)
                .ctlAtual(40.0)
                .atlAtual(20.0)
                .rampRateAtual(5.0)
                .alertaDiasConsecutivos(false)
                .diasConsecutivosTreino(2)
                .fasePeriodizacao(FasePeriodizacao.BUILD)
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .build();

        Atleta atleta = atletaSaudavel(NivelExperiencia.AVANCADO);

        RecomendacaoIntervalado resultado = service.avaliar(atleta, meta, List.of(), DATA_REFERENCIA);

        // Com prontidão = -35 (< -30), deve ser Substituido
        assertInstanceOf(RecomendacaoIntervalado.Substituido.class, resultado,
                "Gate deve bloquear quando prontidão pré-treino está abaixo de -30");
        RecomendacaoIntervalado.Substituido sub = (RecomendacaoIntervalado.Substituido) resultado;
        assertTrue(sub.motivo().contains("-35"),
                "Motivo do bloqueio deve referenciar o valor de TSB de prontidão");
    }
}
