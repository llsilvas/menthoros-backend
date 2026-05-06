package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.services.impl.MetricasAlertaService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
}

