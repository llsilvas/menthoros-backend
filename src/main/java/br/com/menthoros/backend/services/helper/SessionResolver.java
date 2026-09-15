package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.dto.llm.v2.BlocoDto;
import br.com.menthoros.backend.dto.llm.v2.Papel;
import br.com.menthoros.backend.dto.llm.v2.PlanoSemanalLlmDtoV2;
import br.com.menthoros.backend.dto.llm.v2.RecuperacaoDto;
import br.com.menthoros.backend.dto.llm.v2.TreinoPlanejadoLlmDtoV2;
import br.com.menthoros.backend.dto.llm.v2.UnidadeQuantidade;
import br.com.menthoros.backend.dto.llm.v2.Zona;
import br.com.menthoros.backend.services.helper.ZoneResolver.FaixaFc;
import br.com.menthoros.backend.services.helper.ZoneResolver.FaixaPace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolve o schema v2 (blocos qualitativos) em {@link PlanoSemanalLlmDto} — o mesmo shape que o
 * pipeline v1 já consome (etapas absolutas: FC, pace, distância, duração). Roda **uma vez por
 * tentativa**, dentro de {@code gerarChamadaLlm} (branch v2), logo após o parse da resposta única
 * da LLM — não é uma 2ª chamada, é trabalho Java síncrono (semantic-session-schema, design.md §1).
 *
 * <p><b>Não valida nada</b> — não lança para estrutura insuficiente, só produz o resultado
 * aritmético possível. Toda validação estrutural roda depois, dentro de {@code validar}
 * (protegida pelo turno de reparo F3) — ver {@code NormalizacaoDeTreino#validarEstruturaV2}
 * (design.md Decisão 3).</p>
 *
 * <p>Idempotent: YES — cálculo puro sobre os parâmetros recebidos, sem I/O.
 * Side Effects: NONE. Tenant-aware: NO.</p>
 */
@Component
@RequiredArgsConstructor
public class SessionResolver {

    /**
     * Lookup fixo zona-dominante→RPE — valores de exemplo, não confirmados com produto/fisiologia
     * (semantic-session-schema, design.md Decisão 4, task 0.5). Sem precedente no código, ao
     * contrário de FC/pace (que delegam para {@link ZonaTreinoService}, já calibrado).
     */
    private static final Map<Zona, Integer> RPE_POR_ZONA = Map.of(
            Zona.Z1, 2, Zona.Z2, 4, Zona.Z3, 6, Zona.Z4, 8, Zona.Z5, 9, Zona.LIMIAR, 7
    );

    private final ZoneResolver zoneResolver;
    private final TssCalculatorService tssCalculatorService;

    public PlanoSemanalLlmDto resolverPlano(PlanoSemanalLlmDtoV2 planoV2, AthleteZones zonas) {
        List<TreinoPlanejadoLlmDto> treinos = planoV2.treinosPlanejados().stream()
                .map(treinoV2 -> resolverTreino(treinoV2, zonas))
                .toList();

        return PlanoSemanalLlmDto.builder()
                .volumePlanejadoKm(planoV2.volumePlanejadoKm())
                .volumeAlvoKm(planoV2.volumeAlvoKm())
                .tsbInicio(planoV2.tsbInicio())
                .tsbFim(planoV2.tsbFim())
                .status(planoV2.status())
                .objetivoSemanal(planoV2.objetivoSemanal())
                .treinosPlanejados(treinos)
                .build();
    }

    private TreinoPlanejadoLlmDto resolverTreino(TreinoPlanejadoLlmDtoV2 treinoV2, AthleteZones zonas) {
        List<EtapaTreinoLlmDto> etapas = new ArrayList<>();
        List<FaixaFc> faixasFc = new ArrayList<>();
        List<FaixaPace> faixasPace = new ArrayList<>();
        int rpeDominante = 1;

        int ordem = 1;
        for (BlocoDto bloco : treinoV2.blocos()) {
            int repeticoes = bloco.repeticoes() != null ? bloco.repeticoes() : 1;
            String tipoEtapaPrincipal = tipoEtapaDoBloco(bloco.papel(), repeticoes);

            FaixaFc faixaFc = zoneResolver.bpm(bloco.zona(), zonas.fcMaxima(), zonas.fcLimiar());
            FaixaPace faixaPace = zoneResolver.pace(bloco.zona(), zonas.paceLimiar());
            faixasFc.add(faixaFc);
            faixasPace.add(faixaPace);
            rpeDominante = Math.max(rpeDominante, RPE_POR_ZONA.get(bloco.zona()));

            for (int rep = 1; rep <= repeticoes; rep++) {
                etapas.add(construirEtapa(ordem++, tipoEtapaPrincipal, bloco.quantidadePorRepeticao(),
                        bloco.unidade(), faixaFc, faixaPace));

                RecuperacaoDto recuperacao = bloco.recuperacao();
                if (recuperacao != null) {
                    FaixaFc faixaFcRecuperacao = zoneResolver.bpm(Zona.Z1, zonas.fcMaxima(), zonas.fcLimiar());
                    FaixaPace faixaPaceRecuperacao = zoneResolver.pace(Zona.Z1, zonas.paceLimiar());
                    etapas.add(construirEtapa(ordem++, "RECUPERACAO", recuperacao.quantidade(),
                            recuperacao.unidade(), faixaFcRecuperacao, faixaPaceRecuperacao));
                }
            }
        }

        int duracaoTotalMin = etapas.stream().mapToInt(EtapaTreinoLlmDto::duracaoMin).sum();
        double distanciaTotalKm = etapas.stream().mapToDouble(EtapaTreinoLlmDto::distanciaKm).sum();
        int fcMin = faixasFc.stream().mapToInt(FaixaFc::min).min().orElse(0);
        int fcMax = faixasFc.stream().mapToInt(FaixaFc::max).max().orElse(0);
        BigDecimal paceMin = faixasPace.stream().map(FaixaPace::min).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal paceMax = faixasPace.stream().map(FaixaPace::max).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        int tssPlanejado = tssCalculatorService.calcularTssEstimado(Duration.ofMinutes(duracaoTotalMin), rpeDominante);
        double intensidadePlanejada = tssCalculatorService.converterRpeParaIf(rpeDominante);

        return new TreinoPlanejadoLlmDto(
                treinoV2.diaSemana(),
                treinoV2.tipoTreino(),
                fcMin + "-" + fcMax + " bpm",
                tssPlanejado,
                intensidadePlanejada,
                rpeDominante,
                treinoV2.justificativaIa(),
                String.format("%02d:00", duracaoTotalMin),
                distanciaTotalKm,
                formatarPace(paceMin) + "-" + formatarPace(paceMax) + "/km",
                etapas
        );
    }

    private String tipoEtapaDoBloco(Papel papel, int repeticoes) {
        return switch (papel) {
            case AQUEC -> "AQUECIMENTO";
            case DESAQ -> "DESAQUECIMENTO";
            case RECUP -> "RECUPERACAO";
            // repeticoes==1 (não-intervalado, ex. tempo run/longo/regenerativo) vira etapa única
            // PRINCIPAL; repeticoes>1 (intervalado) vira uma etapa INTERVALADO por repetição.
            case PRINCIPAL -> repeticoes > 1 ? "INTERVALADO" : "PRINCIPAL";
        };
    }

    /**
     * Piso de segurança para {@code paceMedio} usado como divisor em {@link #paraKm} — achado do
     * /qa (pré-mortem codex): um `paceLimiar` cadastrado positivo mas ínfimo pode fazer a faixa de
     * pace da zona arredondar para {@code 0.00} (scale 2 em {@code ZonaTreinoService}), causando
     * divisão por zero. `ZoneResolver.pace` já filtra `paceLimiar<=0`; este piso cobre o caso
     * residual de arredondamento. Nunca alcançado por um cadastro fisiológico real (0,01 min/km é
     * absurdamente rápido).
     */
    private static final BigDecimal PACE_MEDIO_PISO_MIN_KM = BigDecimal.valueOf(0.01);

    private EtapaTreinoLlmDto construirEtapa(int ordem, String tipoEtapa, BigDecimal quantidade,
                                              UnidadeQuantidade unidade, FaixaFc faixaFc, FaixaPace faixaPace) {
        BigDecimal paceMedio = faixaPace.min().add(faixaPace.max())
                .divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        if (paceMedio.signum() <= 0) {
            paceMedio = PACE_MEDIO_PISO_MIN_KM;
        }

        // Math.max(1, ...) — mesma convenção de v1 (TreinoNormalizador.expandirEtapasAgregadas:115):
        // etapa com duração sub-minuto (ex. tiro curto em SEG) nunca arredonda para 0 min.
        int duracaoMin = Math.max(1, paraMinutos(quantidade, unidade, paceMedio)
                .setScale(0, RoundingMode.HALF_UP).intValueExact());
        double distanciaKm = paraKm(quantidade, unidade, paceMedio).doubleValue();

        return new EtapaTreinoLlmDto(
                ordem,
                tipoEtapa,
                null,
                duracaoMin,
                distanciaKm,
                faixaFc.min() + "-" + faixaFc.max() + " bpm",
                1,
                formatarPace(faixaPace.min()) + "-" + formatarPace(faixaPace.max()) + "/km"
        );
    }

    private BigDecimal paraMinutos(BigDecimal quantidade, UnidadeQuantidade unidade, BigDecimal paceMedioMinPorKm) {
        return switch (unidade) {
            case MIN -> quantidade;
            case SEG -> quantidade.divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
            case KM -> quantidade.multiply(paceMedioMinPorKm);
            case M -> quantidade.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP).multiply(paceMedioMinPorKm);
        };
    }

    private BigDecimal paraKm(BigDecimal quantidade, UnidadeQuantidade unidade, BigDecimal paceMedioMinPorKm) {
        return switch (unidade) {
            case KM -> quantidade;
            case M -> quantidade.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
            case MIN -> quantidade.divide(paceMedioMinPorKm, 6, RoundingMode.HALF_UP);
            case SEG -> quantidade.divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP)
                    .divide(paceMedioMinPorKm, 6, RoundingMode.HALF_UP);
        };
    }

    // Não reusa PaceValidator.formatarDecimalMinutos (package-private, mas de uma classe de
    // correção de v1) — formatação trivial de "M:SS", duplicar é mais barato que acoplar
    // SessionResolver a uma classe cujo papel é corrigir texto livre da LLM.
    private String formatarPace(BigDecimal decimalMinutos) {
        long totalSegundos = Math.round(decimalMinutos.doubleValue() * 60);
        long minutos = totalSegundos / 60;
        long segundos = totalSegundos % 60;
        return String.format("%d:%02d", minutos, segundos);
    }
}
