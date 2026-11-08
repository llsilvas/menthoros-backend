package br.com.menthoros.backend.dto.fit;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Dados de sessão extraídos de um arquivo .fit — POJO interno, não é DTO de API.
 *
 * @param serialNumber     serial do dispositivo (FileIdMesg) — usado para compor o externalId (D0.2)
 * @param corrida          {@code true} quando {@code Session.Sport == RUNNING}; qualquer outro
 *                         esporte usa {@code tipoTreino = CONTINUO} e o nome do esporte é anotado
 *                         em {@code descricao} pelo chamador (D0.6) — este record só carrega o fato
 *                         bruto, a decisão de mapeamento fica no service de persistência.
 * @param cadenciaMediaPpm cadência em passos por minuto de DUAS pernas (conversão no parser,
 *                         mesma regra do {@link FitLapData}).
 * @param tempoMovimento   tempo em movimento da sessão ({@code getTotalTimerTime()}) — nullable,
 *                         agregado de sessão para simetria com o CSV do Garmin (mesma regra do
 *                         {@link FitLapData}, não usado para corrigir pace de sessão nesta change).
 * @param calorias         calorias totais da sessão ({@code getTotalCalories()}, kcal).
 * @param gctMedioMs           tempo médio de contato com o solo, em ms.
 * @param gctEquilibrioPct     % de GCT do pé esquerdo.
 * @param passadaMediaM        comprimento médio da passada, em metros (FIT entrega em mm).
 * @param oscilacaoVerticalCm  oscilação vertical média, em cm (FIT entrega em mm).
 * @param proporcaoVerticalPct proporção vertical média, em %.
 * @param temperaturaMediaC    temperatura média, em °C.
 * @param velocidadeMediaKmh   distância / tempo em movimento da sessão, em km/h; nulo sem um dos dois.
 * @param velocidadeMaxKmh     {@code enhanced_max_speed} (fallback {@code max_speed}), em km/h.
 * @param temperaturaMinC      temperatura mínima, em °C.
 * @param temperaturaMaxC      temperatura máxima, em °C.
 * @param amostrasFc           mensagens {@code record} em ordem de arquivo, para o tempo em zonas;
 *                             vazia quando o arquivo passa do limite de amostras. Não é persistida.
 * @param gpsSamples           pontos GPS já decimados (add-activity-gps-trace, D3); vazia sem GPS.
 * @param deviceName           {@code DeviceInfoMesg.productName} (fallback {@code FileIdMesg}), ex.
 *                             {@code "Forerunner 265"}; nulo quando o arquivo não informa. Só é usado
 *                             como fallback do {@code deviceName} do ping (D1).
 */
public record FitSessionData(
        Long serialNumber,
        LocalDate dataTreino,
        long startTimeEpochSeconds,
        Duration duracao,
        Double distanciaKm,
        Integer fcMedia,
        Integer fcMax,
        Integer tssCalculado,
        boolean corrida,
        String esporteDetectado,
        Integer subidaMetros,
        Integer descidaMetros,
        Integer potenciaMediaWatts,
        Integer cadenciaMediaPpm,
        Duration tempoMovimento,
        Integer calorias,
        Integer gctMedioMs,
        BigDecimal gctEquilibrioPct,
        BigDecimal passadaMediaM,
        BigDecimal oscilacaoVerticalCm,
        BigDecimal proporcaoVerticalPct,
        BigDecimal temperaturaMediaC,
        List<FitLapData> laps,
        Double velocidadeMediaKmh,
        BigDecimal velocidadeMaxKmh,
        BigDecimal temperaturaMinC,
        BigDecimal temperaturaMaxC,
        List<FitAmostraFc> amostrasFc,
        List<FitGpsSample> gpsSamples,
        @Nullable String deviceName
) {

    /** Antes do trajeto GPS (add-activity-gps-trace) — mantém os chamadores e testes anteriores. */
    public FitSessionData(Long serialNumber, LocalDate dataTreino, long startTimeEpochSeconds, Duration duracao,
                          Double distanciaKm, Integer fcMedia, Integer fcMax, Integer tssCalculado, boolean corrida,
                          String esporteDetectado, Integer subidaMetros, Integer descidaMetros,
                          Integer potenciaMediaWatts, Integer cadenciaMediaPpm, Duration tempoMovimento,
                          Integer calorias, Integer gctMedioMs, BigDecimal gctEquilibrioPct, BigDecimal passadaMediaM,
                          BigDecimal oscilacaoVerticalCm, BigDecimal proporcaoVerticalPct,
                          BigDecimal temperaturaMediaC, List<FitLapData> laps, Double velocidadeMediaKmh,
                          BigDecimal velocidadeMaxKmh, BigDecimal temperaturaMinC, BigDecimal temperaturaMaxC,
                          List<FitAmostraFc> amostrasFc) {
        this(serialNumber, dataTreino, startTimeEpochSeconds, duracao, distanciaKm, fcMedia, fcMax, tssCalculado,
                corrida, esporteDetectado, subidaMetros, descidaMetros, potenciaMediaWatts, cadenciaMediaPpm,
                tempoMovimento, calorias, gctMedioMs, gctEquilibrioPct, passadaMediaM, oscilacaoVerticalCm,
                proporcaoVerticalPct, temperaturaMediaC, laps, velocidadeMediaKmh, velocidadeMaxKmh,
                temperaturaMinC, temperaturaMaxC, amostrasFc, List.of(), null);
    }

    /** Sem os campos de execução (coach-ver-treino-realizado) — usado pelos testes anteriores a eles. */
    public FitSessionData(Long serialNumber, LocalDate dataTreino, long startTimeEpochSeconds, Duration duracao,
                          Double distanciaKm, Integer fcMedia, Integer fcMax, Integer tssCalculado, boolean corrida,
                          String esporteDetectado, Integer subidaMetros, Integer descidaMetros,
                          Integer potenciaMediaWatts, Integer cadenciaMediaPpm, Duration tempoMovimento,
                          Integer calorias, Integer gctMedioMs, BigDecimal gctEquilibrioPct, BigDecimal passadaMediaM,
                          BigDecimal oscilacaoVerticalCm, BigDecimal proporcaoVerticalPct,
                          BigDecimal temperaturaMediaC, List<FitLapData> laps) {
        this(serialNumber, dataTreino, startTimeEpochSeconds, duracao, distanciaKm, fcMedia, fcMax, tssCalculado,
                corrida, esporteDetectado, subidaMetros, descidaMetros, potenciaMediaWatts, cadenciaMediaPpm,
                tempoMovimento, calorias, gctMedioMs, gctEquilibrioPct, passadaMediaM, oscilacaoVerticalCm,
                proporcaoVerticalPct, temperaturaMediaC, laps, null, null, null, null, List.of());
    }
}
