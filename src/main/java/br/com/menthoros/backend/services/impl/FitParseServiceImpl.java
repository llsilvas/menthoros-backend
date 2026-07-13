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

    @Override
    public FitSessionData parse(InputStream in) {
        List<FitLapData> laps = new ArrayList<>();
        AtomicLong serialNumber = new AtomicLong(0);
        AtomicInteger lapOrdem = new AtomicInteger(1);
        AtomicInteger sessionCount = new AtomicInteger(0);
        AtomicReference<FitSessionData> resultado = new AtomicReference<>();

        MesgBroadcaster broadcaster = new MesgBroadcaster();

        broadcaster.addListener((FileIdMesgListener) mesg -> {
            if (mesg.getSerialNumber() != null) {
                serialNumber.set(mesg.getSerialNumber());
            }
        });

        broadcaster.addListener((LapMesgListener) mesg -> {
            if (laps.size() >= MAX_LAPS) {
                throw new FitParseException("Arquivo .fit excede o número máximo de laps permitido (" + MAX_LAPS + ").");
            }
            laps.add(new FitLapData(
                    lapOrdem.getAndIncrement(),
                    duracaoDeSegundos(mesg.getTotalElapsedTime()),
                    distanciaKmDeMetros(mesg.getTotalDistance()),
                    inteiroOuNulo(mesg.getAvgHeartRate()),
                    inteiroOuNulo(mesg.getMaxHeartRate()),
                    mesg.getTotalAscent(),
                    mesg.getTotalDescent(),
                    mesg.getAvgPower(),
                    cadenciaPpm(primeiroNaoNulo(mesg.getAvgRunningCadence(), mesg.getAvgCadence()),
                            mesg.getAvgFractionalCadence())
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
            boolean corrida = sport == Sport.RUNNING;
            Instant startInstant = mesg.getStartTime().getDate().toInstant();

            resultado.set(new FitSessionData(
                    serialNumber.get() != 0 ? serialNumber.get() : null,
                    startInstant.atZone(ZoneId.systemDefault()).toLocalDate(),
                    startInstant.getEpochSecond(),
                    duracaoDeSegundos(mesg.getTotalElapsedTime()),
                    distanciaKmDeMetros(mesg.getTotalDistance()),
                    inteiroOuNulo(mesg.getAvgHeartRate()),
                    inteiroOuNulo(mesg.getMaxHeartRate()),
                    mesg.getTrainingStressScore() != null ? Math.round(mesg.getTrainingStressScore()) : null,
                    corrida,
                    sport != null ? sport.name() : "GENERIC",
                    mesg.getTotalAscent(),
                    mesg.getTotalDescent(),
                    mesg.getAvgPower(),
                    cadenciaPpm(primeiroNaoNulo(mesg.getAvgRunningCadence(), mesg.getAvgCadence()),
                            mesg.getAvgFractionalCadence()),
                    laps
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

        FitSessionData dados = resultado.get();
        if (dados == null) {
            throw new FitParseException("Nenhuma mensagem Session encontrada no arquivo FIT.");
        }
        return dados;
    }

    private static Duration duracaoDeSegundos(Float segundos) {
        if (segundos == null) return Duration.ZERO;
        return Duration.ofMillis(Math.round(segundos * 1000));
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
     * cadência sumir; o fallback lê o campo bruto (mesmo valor físico).
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
