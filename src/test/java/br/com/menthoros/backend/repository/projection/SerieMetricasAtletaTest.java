package br.com.menthoros.backend.repository.projection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SerieMetricasAtleta.primeiroDiaPendente")
class SerieMetricasAtletaTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 20);

    private static SerieMetricasAtleta serie(LocalDate ultima, LocalDate buraco) {
        return new SerieMetricasAtleta() {
            @Override public UUID getAtletaId() { return UUID.randomUUID(); }
            @Override public LocalDate getUltimaData() { return ultima; }
            @Override public LocalDate getPrimeiroBuraco() { return buraco; }
        };
    }

    @Test
    @DisplayName("buraco no meio da série: o pendente é o buraco, mesmo com a série em dia")
    void buracoVenceMesmoEmDia() {
        assertThat(serie(HOJE, HOJE.minusDays(5)).primeiroDiaPendente(HOJE))
                .contains(HOJE.minusDays(5));
    }

    @Test
    @DisplayName("série densa com última métrica antes de hoje: pendente é ultima + 1")
    void densaAtrasada() {
        assertThat(serie(HOJE.minusDays(3), null).primeiroDiaPendente(HOJE))
                .contains(HOJE.minusDays(2));
    }

    @Test
    @DisplayName("série densa e em dia (última = hoje): nada pendente")
    void densaEmDia() {
        assertThat(serie(HOJE, null).primeiroDiaPendente(HOJE)).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("última métrica no futuro do atleta (fuso adiantado): nada pendente")
    void ultimaNoFuturo() {
        assertThat(serie(HOJE.plusDays(1), null).primeiroDiaPendente(HOJE)).isEmpty();
    }
}
