package br.com.menthoros.backend.repository.projection;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Resumo da série de {@code tb_metricas_diarias} de um atleta: o suficiente para decidir, sem ler
 * linha a linha, se há dia a materializar e por onde começar
 * (fix-tsb-rest-days-materialization, design.md R3).
 */
public interface SerieMetricasAtleta {

    UUID getAtletaId();

    /** Última data com linha. */
    LocalDate getUltimaData();

    /** Primeiro dia ausente entre a primeira e a última métrica; {@code null} se a série é densa. */
    LocalDate getPrimeiroBuraco();

    /**
     * Primeiro dia a recalcular, dado o "hoje" do atleta. Um buraco no meio vence sempre — mesmo com
     * a série em dia, pois as linhas depois dele foram calculadas sobre seed errado. Sem buraco, é o
     * dia seguinte à última métrica quando ela é anterior a hoje.
     *
     * <p>Independe de {@code MAX(data)} para dizer "em dia": a última data sozinha não distingue
     * série correta de série com buraco reparado pela metade.</p>
     */
    default Optional<LocalDate> primeiroDiaPendente(LocalDate hoje) {
        if (getPrimeiroBuraco() != null) {
            return Optional.of(getPrimeiroBuraco());
        }
        return getUltimaData().isBefore(hoje) ? Optional.of(getUltimaData().plusDays(1)) : Optional.empty();
    }
}
