package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.output.LapEfficiencySeriesDto;
import br.com.menthoros.backend.dto.output.LapEfficiencySeriesDto.LapEfficiencyPointDto;
import br.com.menthoros.backend.dto.output.LapEfficiencySeriesDto.VoltaOmitidaDto;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.MotivoOmissaoVolta;
import br.com.menthoros.backend.enums.OrigemCalculo;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Série de eficiência (EF) por volta — o "decoupling volta a volta" (design D1 de
 * fit-lap-derived-metrics). Elegibilidade POR PONTO: série parcial com buracos é aceitável e os
 * metadados de omissão explicam cada buraco; diferente do decoupling escalar, que mantém gates
 * de treino inteiro no {@link DecouplingCalculatorService}.
 *
 * <p>Derivada na leitura, nunca persistida. O {@code paceGap} só é preenchido quando o hard gate
 * do GAP ({@link GapCalculator#HABILITADO}) estiver liberado.
 *
 * <p>Idempotent: YES — cálculo puro, sem estado. Side Effects: NONE. Tenant-aware: NO.
 */
@Component
public class LapEfficiencySeriesCalculator {

    /**
     * Adaptador para o MapStruct: série do treino com o peso do atleta (para W/kg), resolvido
     * null-safe — o mapper usa via {@code qualifiedByName} apenas no fluxo de detalhe.
     *
     * <p>Idempotent: YES. Side Effects: NONE. Tenant-aware: NO.
     */
    @Named("serieEficienciaDeTreino")
    public LapEfficiencySeriesDto serieDeTreino(TreinoRealizado treino) {
        if (treino == null) {
            return null;
        }
        BigDecimal pesoKg = treino.getAtleta() != null ? treino.getAtleta().getPesoKg() : null;
        return calcular(treino.getEtapasRealizadas(), pesoKg);
    }

    /**
     * Calcula a série de EF por volta, ou {@code null} quando não há etapas.
     *
     * <p>Idempotent: YES — cálculo puro, sem estado. Side Effects: NONE. Tenant-aware: NO.
     */
    public LapEfficiencySeriesDto calcular(List<EtapaRealizada> etapas, BigDecimal pesoKg) {
        if (etapas == null) {
            return null;
        }
        List<EtapaRealizada> voltas = etapas.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(e -> e.getOrdem() != null ? e.getOrdem() : 0))
                .toList();
        if (voltas.isEmpty()) {
            return null;
        }

        List<LapEfficiencyPointDto> pontos = new ArrayList<>();
        List<VoltaOmitidaDto> omitidas = new ArrayList<>();
        for (EtapaRealizada volta : voltas) {
            int ordem = volta.getOrdem() != null ? volta.getOrdem() : 0;
            MotivoOmissaoVolta motivo = motivoOmissao(volta);
            if (motivo != null) {
                omitidas.add(new VoltaOmitidaDto(ordem, motivo));
                continue;
            }
            pontos.add(ponto(ordem, volta, pesoKg));
        }

        return new LapEfficiencySeriesDto(OrigemCalculo.POR_VOLTA, voltas.size(), omitidas, pontos);
    }

    private static MotivoOmissaoVolta motivoOmissao(EtapaRealizada volta) {
        Duration duracao = volta.getDuracao();
        if (duracao == null || duracao.isZero() || duracao.isNegative()) {
            return MotivoOmissaoVolta.DURACAO_INVALIDA;
        }
        if (volta.getFcMedia() == null || volta.getFcMedia() <= 0) {
            return MotivoOmissaoVolta.SEM_FC;
        }
        Double velocidade = DecouplingCalculatorService.velocidadeKmh(volta);
        if (velocidade == null || velocidade <= 0) {
            return MotivoOmissaoVolta.SEM_VELOCIDADE;
        }
        return null;
    }

    private static LapEfficiencyPointDto ponto(int ordem, EtapaRealizada volta, BigDecimal pesoKg) {
        double velocidade = DecouplingCalculatorService.velocidadeKmh(volta);
        int fc = volta.getFcMedia();
        Integer potencia = volta.getPotenciaMedia() != null && volta.getPotenciaMedia() > 0
                ? volta.getPotenciaMedia() : null;

        Double efPotencia = potencia != null ? arredondar4(potencia / (double) fc) : null;
        Double wPorKg = potencia != null && pesoKg != null && pesoKg.signum() > 0
                ? arredondar2(potencia / pesoKg.doubleValue()) : null;

        return new LapEfficiencyPointDto(
                ordem,
                arredondar2(velocidade),
                fc,
                arredondar4(velocidade / fc),
                efPotencia,
                wPorKg,
                paceGapFormatado(volta)
        );
    }

    /** Só produz valor com o hard gate liberado (matriz de calibração verde — design D2). */
    private static String paceGapFormatado(EtapaRealizada volta) {
        if (!GapCalculator.HABILITADO) {
            return null;
        }
        Double velocidade = DecouplingCalculatorService.velocidadeKmh(volta);
        if (velocidade == null || velocidade <= 0) {
            return null;
        }
        Duration paceBruto = Duration.ofSeconds(Math.round(3600.0 / velocidade));
        Duration gap = GapCalculator.paceGap(paceBruto,
                volta.getElevacaoGanhoMetros(), volta.getElevacaoPerdaMetros(),
                volta.getDistanciaKm() != null ? volta.getDistanciaKm().doubleValue() : null);
        if (gap == null) {
            return null;
        }
        return "%d:%02d".formatted(gap.toMinutes(), gap.toSecondsPart());
    }

    private static Double arredondar2(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    private static Double arredondar4(double valor) {
        return Math.round(valor * 10_000.0) / 10_000.0;
    }
}
