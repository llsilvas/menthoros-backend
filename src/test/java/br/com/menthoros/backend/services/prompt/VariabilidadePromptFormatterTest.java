package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.TipoTreino;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariabilidadePromptFormatterTest {

    @Test
    void analisarEstimulosRecentes_deveRotularSemanasComJanelaCorreta() {
        VariabilidadePromptFormatter formatter = new VariabilidadePromptFormatter();
        LocalDate referencia = LocalDate.of(2026, 2, 17);

        TreinoRealizado t0 = new TreinoRealizado();
        t0.setDataTreino(referencia.minusDays(2));
        t0.setTipoTreino(TipoTreino.CONTINUO);
        t0.setDistanciaKm(BigDecimal.valueOf(8));
        t0.setTssCalculado(40);
        t0.setZonaAlvo("Z2");
        t0.setPercepcaoEsforco(4);

        TreinoRealizado t1 = new TreinoRealizado();
        t1.setDataTreino(referencia.minusDays(9));
        t1.setTipoTreino(TipoTreino.LONGO);
        t1.setDistanciaKm(BigDecimal.valueOf(16));
        t1.setTssCalculado(90);
        t1.setZonaAlvo("Z2");
        t1.setPercepcaoEsforco(6);

        TreinoRealizado t2 = new TreinoRealizado();
        t2.setDataTreino(referencia.minusDays(16));
        t2.setTipoTreino(TipoTreino.REGENERATIVO);
        t2.setDistanciaKm(BigDecimal.valueOf(5));
        t2.setTssCalculado(20);
        t2.setZonaAlvo("Z1");
        t2.setPercepcaoEsforco(2);

        String texto = formatter.analisarEstimulosRecentes(List.of(t0, t1, t2), referencia);
        assertTrue(texto.contains("Atual (0–6 dias atrás)"));
        assertTrue(texto.contains("Anterior (7–13 dias atrás)"));
        assertTrue(texto.contains("Base (14–20 dias atrás)"));
    }

    @Test
    void gerarAlertasVariabilidade_naoDeveAcusarLongoAusenteQuandoFoiPrescritoECumprido() {
        // O sync classificou o longão como TEMPO_RUN. Sem resolver pela prescrição, o alerta manda
        // "REINTRODUZIR" um estímulo que o atleta fez ontem — e ainda conta o longão como intensivo.
        VariabilidadePromptFormatter formatter = new VariabilidadePromptFormatter();
        LocalDate referencia = LocalDate.of(2026, 2, 17);

        String texto = formatter.gerarAlertasVariabilidade(List.of(
                vinculado(treino(referencia.minusDays(1), TipoTreino.TEMPO_RUN), TipoTreino.LONGO)), referencia);

        assertFalse(texto.contains("**LONGO:** NUNCA realizado"),
                "o longão prescrito e cumprido não pode aparecer como nunca realizado");
        assertTrue(texto.contains("**TEMPO_RUN:** NUNCA realizado"),
                "o TEMPO_RUN é que não foi realizado — o tipo inferido não pode mascarar isso");
    }

    @Test
    void gerarAlertasVariabilidade_naoDeveContarLongaoPrescritoComoTreinoIntensivo() {
        VariabilidadePromptFormatter formatter = new VariabilidadePromptFormatter();
        LocalDate referencia = LocalDate.of(2026, 2, 17);

        String texto = formatter.gerarAlertasVariabilidade(List.of(
                vinculado(treino(referencia.minusDays(1), TipoTreino.TEMPO_RUN), TipoTreino.LONGO),
                treino(referencia.minusDays(3), TipoTreino.FACIL)), referencia);

        // 0 de 2 treinos são intensivos — com o tipo inferido seriam 50% e o prompt pediria menos intensidade.
        assertTrue(texto.contains("Baixa frequência de treinos intensivos (0%"), texto);
    }

    private TreinoRealizado treino(LocalDate data, TipoTreino tipo) {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setDataTreino(data);
        tr.setTipoTreino(tipo);
        tr.setDistanciaKm(BigDecimal.valueOf(15));
        tr.setTssCalculado(80);
        tr.setZonaAlvo("Z2");
        tr.setPercepcaoEsforco(6);
        return tr;
    }

    private TreinoRealizado vinculado(TreinoRealizado realizado, TipoTreino tipoPrescrito) {
        TreinoPlanejado planejado = new TreinoPlanejado();
        planejado.setTipoTreino(tipoPrescrito);
        realizado.setTreinoPlanejado(planejado);
        return realizado;
    }
}

