package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.fit.FitLapData;
import br.com.menthoros.backend.dto.fit.FitSessionData;
import br.com.menthoros.backend.exception.FitParseException;
import br.com.menthoros.backend.services.FitParseService;
import com.garmin.fit.Decode;
import com.garmin.fit.FileIdMesgListener;
import com.garmin.fit.LapMesgListener;
import com.garmin.fit.MesgBroadcaster;
import com.garmin.fit.SessionMesgListener;
import com.garmin.fit.Sport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class FitParseServiceImpl implements FitParseService {

    /**
     * Nenhum dispositivo real produz mais laps que isso num único treino — um arquivo que
     * excede o limite é tratado como hostil (flood de mensagens Lap) e rejeitado antes de
     * consumir memória/DB desproporcionalmente.
     */
    private static final int MAX_LAPS = 1000;

    /**
     * Lap cru, ainda sem interpretação dependente do esporte: a conversão de cadência
     * (uma perna → duas pernas) só vale para corrida, e o esporte vem da mensagem Session —
     * que não tem ordem garantida em relação aos Laps no stream. Por isso os listeners só
     * coletam dados crus e o resultado final é montado APÓS o decode completo.
     */
    private record LapBruto(
            int ordem,
            Float totalElapsedTime,
            Float totalDistance,
            Short avgHeartRate,
            Short maxHeartRate,
            Integer totalAscent,
            Integer totalDescent,
            Integer avgPower,
            Short cadenciaUmaPerna,
            Float cadenciaFracionaria,
            Float totalTimerTime,
            Float avgStanceTime,
            Float avgStanceTimeBalance,
            Float avgStepLength,
            Float avgVerticalOscillation,
            Float avgVerticalRatio,
            Byte avgTemperature
    ) {}

    /**
     * Decodifica um arquivo .fit e extrai os dados estruturados de sessão e laps.
     *
     * <p><b>Idempotent:</b> YES — parse puro do stream, sem estado entre chamadas.
     * <b>Side Effects:</b> NONE — apenas lê e transforma; nada de banco/contexto.
     * <b>Tenant-aware:</b> NO — tenant é resolvido pelo chamador ({@code FitTreinoPersister}).
     *
     * @throws FitParseException arquivo inválido, corrompido, multiessão ou sem startTime
     */
    @Override
    public FitSessionData parse(InputStream in) {
        List<LapBruto> lapsBrutos = new ArrayList<>();
        AtomicLong serialNumber = new AtomicLong(0);
        AtomicInteger lapOrdem = new AtomicInteger(1);
        AtomicInteger sessionCount = new AtomicInteger(0);
        AtomicReference<SessaoBruta> sessao = new AtomicReference<>();

        MesgBroadcaster broadcaster = new MesgBroadcaster();

        broadcaster.addListener((FileIdMesgListener) mesg -> {
            if (mesg.getSerialNumber() != null) {
                serialNumber.set(mesg.getSerialNumber());
            }
        });

        broadcaster.addListener((LapMesgListener) mesg -> {
            if (lapsBrutos.size() >= MAX_LAPS) {
                throw new FitParseException("Arquivo .fit excede o número máximo de laps permitido (" + MAX_LAPS + ").");
            }
            lapsBrutos.add(new LapBruto(
                    lapOrdem.getAndIncrement(),
                    mesg.getTotalElapsedTime(),
                    mesg.getTotalDistance(),
                    mesg.getAvgHeartRate(),
                    mesg.getMaxHeartRate(),
                    mesg.getTotalAscent(),
                    mesg.getTotalDescent(),
                    mesg.getAvgPower(),
                    primeiroNaoNulo(mesg.getAvgRunningCadence(), mesg.getAvgCadence()),
                    mesg.getAvgFractionalCadence(),
                    mesg.getTotalTimerTime(),
                    mesg.getAvgStanceTime(),
                    mesg.getAvgStanceTimeBalance(),
                    mesg.getAvgStepLength(),
                    mesg.getAvgVerticalOscillation(),
                    mesg.getAvgVerticalRatio(),
                    mesg.getAvgTemperature()
            ));
        });

        broadcaster.addListener((SessionMesgListener) mesg -> {
            if (sessionCount.incrementAndGet() > 1) {
                // Arquivo multiesporte/multiessão (ex.: triathlon) — laps de sessões diferentes
                // seriam misturados sob uma única Session, corrompendo FC/duração/distância.
                // Rejeitar em vez de mesclar silenciosamente (D0.6 assume esporte único por sessão).
                throw new FitParseException("Arquivo .fit contém múltiplas mensagens Session — não suportado.");
            }
            if (mesg.getStartTime() == null) {
                // Sem startTime não há como compor um externalId estável — fabricar um timestamp
                // com Instant.now() quebraria a garantia de idempotência do reenvio do mesmo .fit.
                throw new FitParseException("Arquivo .fit sem horário de início (Session.StartTime) — não é possível processar.");
            }
            Sport sport = mesg.getSport();
            sessao.set(new SessaoBruta(
                    mesg.getStartTime().getDate().toInstant(),
                    mesg.getTotalElapsedTime(),
                    mesg.getTotalDistance(),
                    mesg.getAvgHeartRate(),
                    mesg.getMaxHeartRate(),
                    mesg.getTrainingStressScore(),
                    sport == Sport.RUNNING,
                    sport != null ? sport.name() : "GENERIC",
                    mesg.getTotalAscent(),
                    mesg.getTotalDescent(),
                    mesg.getAvgPower(),
                    primeiroNaoNulo(mesg.getAvgRunningCadence(), mesg.getAvgCadence()),
                    mesg.getAvgFractionalCadence(),
                    mesg.getTotalTimerTime(),
                    mesg.getTotalCalories(),
                    mesg.getAvgStanceTime(),
                    mesg.getAvgStanceTimeBalance(),
                    mesg.getAvgStepLength(),
                    mesg.getAvgVerticalOscillation(),
                    mesg.getAvgVerticalRatio(),
                    mesg.getAvgTemperature()
            ));
        });

        try {
            new Decode().read(in, broadcaster);
        } catch (FitParseException e) {
            // Já é a exceção/mensagem certa (lançada pelos nossos próprios listeners) — não reenvelopar.
            throw e;
        } catch (RuntimeException e) {
            // com.garmin.fit.FitRuntimeException cobre a maioria dos casos de stream malformado,
            // mas um binário adversarial pode disparar outros RuntimeException inesperados dentro
            // do SDK — tratar qualquer um deles como .fit inválido em vez de vazar um 500 genérico.
            log.warn("Falha ao decodificar arquivo .fit: {}", e.getMessage());
            throw new FitParseException("Arquivo inválido ou corrompido — não é um .fit válido.", e);
        }

        SessaoBruta s = sessao.get();
        if (s == null) {
            throw new FitParseException("Nenhuma mensagem Session encontrada no arquivo FIT.");
        }
        return montarResultado(s, lapsBrutos, serialNumber.get());
    }

    /** Sessão crua capturada no listener — resultado final montado após o decode (ver {@link LapBruto}). */
    private record SessaoBruta(
            Instant startInstant,
            Float totalElapsedTime,
            Float totalDistance,
            Short avgHeartRate,
            Short maxHeartRate,
            Float trainingStressScore,
            boolean corrida,
            String esporte,
            Integer totalAscent,
            Integer totalDescent,
            Integer avgPower,
            Short cadenciaUmaPerna,
            Float cadenciaFracionaria,
            Float totalTimerTime,
            Integer totalCalories,
            Float avgStanceTime,
            Float avgStanceTimeBalance,
            Float avgStepLength,
            Float avgVerticalOscillation,
            Float avgVerticalRatio,
            Byte avgTemperature
    ) {}

    private static FitSessionData montarResultado(SessaoBruta s, List<LapBruto> lapsBrutos, long serialNumber) {
        // Cadência só existe no domínio como passos/min de corrida — para outros esportes o
        // campo bruto é RPM (pedal, braçada) e dobrá-lo fabricaria um valor fantasma; fica null.
        boolean corrida = s.corrida();
        List<FitLapData> laps = lapsBrutos.stream()
                .map(lap -> new FitLapData(
                        lap.ordem(),
                        duracaoDeSegundos(lap.totalElapsedTime()),
                        distanciaKmDeMetros(lap.totalDistance()),
                        inteiroOuNulo(lap.avgHeartRate()),
                        inteiroOuNulo(lap.maxHeartRate()),
                        lap.totalAscent(),
                        lap.totalDescent(),
                        lap.avgPower(),
                        corrida ? cadenciaPpm(lap.cadenciaUmaPerna(), lap.cadenciaFracionaria()) : null,
                        tempoMovimentoDeSegundos(lap.totalTimerTime()),
                        inteiroArredondado(lap.avgStanceTime()),
                        bigDecimal1Casa(lap.avgStanceTimeBalance()),
                        metrosDeMilimetros(lap.avgStepLength()),
                        centimetrosDeMilimetros(lap.avgVerticalOscillation()),
                        bigDecimal1Casa(lap.avgVerticalRatio()),
                        celsiusDeByte(lap.avgTemperature())
                ))
                .toList();

        return new FitSessionData(
                serialNumber != 0 ? serialNumber : null,
                s.startInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                s.startInstant().getEpochSecond(),
                duracaoDeSegundos(s.totalElapsedTime()),
                distanciaKmDeMetros(s.totalDistance()),
                inteiroOuNulo(s.avgHeartRate()),
                inteiroOuNulo(s.maxHeartRate()),
                s.trainingStressScore() != null ? Math.round(s.trainingStressScore()) : null,
                corrida,
                s.esporte(),
                s.totalAscent(),
                s.totalDescent(),
                s.avgPower(),
                corrida ? cadenciaPpm(s.cadenciaUmaPerna(), s.cadenciaFracionaria()) : null,
                tempoMovimentoDeSegundos(s.totalTimerTime()),
                s.totalCalories(),
                inteiroArredondado(s.avgStanceTime()),
                bigDecimal1Casa(s.avgStanceTimeBalance()),
                metrosDeMilimetros(s.avgStepLength()),
                centimetrosDeMilimetros(s.avgVerticalOscillation()),
                bigDecimal1Casa(s.avgVerticalRatio()),
                celsiusDeByte(s.avgTemperature()),
                laps
        );
    }

    private static Duration duracaoDeSegundos(Float segundos) {
        if (segundos == null) return Duration.ZERO;
        return Duration.ofMillis(Math.round(segundos * 1000));
    }

    /**
     * Tempo em movimento ({@code getTotalTimerTime()}) — DIFERENTE de {@link #duracaoDeSegundos},
     * que fabrica {@code Duration.ZERO} para {@code totalElapsedTime} (sempre presente num lap/
     * sessão válidos). Timer time é opcional (falta em dispositivos antigos sem o campo) — regra
     * transversal "getter null → coluna null" (design D2 de fit-running-dynamics-ingestion).
     */
    private static Duration tempoMovimentoDeSegundos(Float segundos) {
        return segundos != null ? Duration.ofMillis(Math.round(segundos * 1000)) : null;
    }

    private static Integer inteiroArredondado(Float valor) {
        return valor != null ? Math.round(valor) : null;
    }

    /** GCT-equilíbrio/proporção vertical: % direto do SDK, arredondado para 1 casa. */
    private static BigDecimal bigDecimal1Casa(Float valor) {
        return valor != null ? BigDecimal.valueOf(valor).setScale(1, RoundingMode.HALF_UP) : null;
    }

    /** Passada: a FIT entrega em mm; o domínio usa metros (2 casas). */
    private static BigDecimal metrosDeMilimetros(Float milimetros) {
        return milimetros != null
                ? BigDecimal.valueOf(milimetros / 1000.0).setScale(2, RoundingMode.HALF_UP)
                : null;
    }

    /** Oscilação vertical: a FIT entrega em mm; o domínio usa centímetros (1 casa). */
    private static BigDecimal centimetrosDeMilimetros(Float milimetros) {
        return milimetros != null
                ? BigDecimal.valueOf(milimetros / 10.0).setScale(1, RoundingMode.HALF_UP)
                : null;
    }

    private static BigDecimal celsiusDeByte(Byte graus) {
        return graus != null ? BigDecimal.valueOf(graus).setScale(1, RoundingMode.HALF_UP) : null;
    }

    private static Double distanciaKmDeMetros(Float metros) {
        return metros != null ? metros / 1000.0 : null;
    }

    private static Integer inteiroOuNulo(Short valor) {
        return valor != null ? valor.intValue() : null;
    }

    /**
     * {@code avgRunningCadence} é um SUBFIELD de {@code avg_cadence} que só resolve quando a
     * mensagem tem {@code sport} preenchido — dispositivos que omitem o sport no lap fariam a
     * cadência sumir; o fallback lê o campo bruto (mesmo valor físico). Seguro porque a conversão
     * para ppm só é aplicada quando a Session declara corrida (RPM de outros esportes vira null).
     */
    private static Short primeiroNaoNulo(Short preferido, Short fallback) {
        return preferido != null ? preferido : fallback;
    }

    /**
     * A FIT grava cadência de corrida em passos de UMA perna/min ({@code avgRunningCadence}),
     * com a parte fracionária em {@code avgFractionalCadence} — o valor exibido pelos relógios
     * (e esperado pelo domínio) é o de duas pernas: {@code (inteiro + fração) * 2}.
     */
    private static Integer cadenciaPpm(Short cadenciaUmaPerna, Float fracional) {
        if (cadenciaUmaPerna == null) {
            return null;
        }
        float passosUmaPerna = cadenciaUmaPerna + (fracional != null ? fracional : 0f);
        return Math.round(passosUmaPerna * 2f);
    }
}
