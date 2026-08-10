package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.services.impl.MetricasAlertaService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertasPromptFormatterTest {

    @Test
    void gerarAlertasObrigatorios_deveTratarListaDeTreinosNula() {
        AlertasPromptFormatter formatter = new AlertasPromptFormatter(new MetricasAlertaService());

        Atleta atleta = Atleta.builder()
                .nome("Teste")
                .objetivo("Corrida")
                .temLesao(false)
                .build();

        PlanoMetaDados metaDados = PlanoMetaDados.builder()
                .tsbAtual(-40.0)
                .rampRateAtual(0.0)
                .diasConsecutivosTreino(0)
                .semanasProgressaoContinua(0)
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .atleta(atleta)
                .build();

        String texto = assertDoesNotThrow(() -> formatter.gerarAlertasObrigatorios(
                atleta,
                metaDados,
                5,
                null,
                LocalDate.of(2026, 2, 17)
        ));

        assertTrue(texto.contains("ALERTAS OBRIGATÓRIOS") || texto.contains("FADIGA CRÍTICA"));
    }

    @Test
    void extrairUltimaDataPorTipo_deveIndexarPelaPrescricaoQuandoHaVinculo() {
        // O alerta de "estímulo ausente" é indexado por tipo. Com o tipo inferido pelo sync, um
        // longão prescrito e cumprido entra como TEMPO_RUN: o LONGO aparece como ausente e o prompt
        // manda reintroduzir uma sessão que o atleta acabou de fazer.
        AlertasPromptFormatter formatter = new AlertasPromptFormatter(new MetricasAlertaService());
        LocalDate data = LocalDate.of(2026, 2, 15);

        Map<String, LocalDate> mapa = formatter.extrairUltimaDataPorTipo(List.of(
                vinculado(treino(data, TipoTreino.TEMPO_RUN), TipoTreino.LONGO)));

        assertEquals(Map.of("LONGO", data), mapa);
    }

    @Test
    void extrairUltimaDataPorTipo_deveManterTipoExecutadoSemVinculo() {
        AlertasPromptFormatter formatter = new AlertasPromptFormatter(new MetricasAlertaService());
        LocalDate data = LocalDate.of(2026, 2, 15);

        Map<String, LocalDate> mapa = formatter.extrairUltimaDataPorTipo(List.of(
                treino(data, TipoTreino.TEMPO_RUN)));

        assertEquals(Map.of("TEMPO_RUN", data), mapa);
    }

    private TreinoRealizado treino(LocalDate data, TipoTreino tipo) {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setDataTreino(data);
        tr.setTipoTreino(tipo);
        return tr;
    }

    private TreinoRealizado vinculado(TreinoRealizado realizado, TipoTreino tipoPrescrito) {
        TreinoPlanejado planejado = new TreinoPlanejado();
        planejado.setTipoTreino(tipoPrescrito);
        realizado.setTreinoPlanejado(planejado);
        return realizado;
    }
}

