package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.fit.FitLapData;
import br.com.menthoros.backend.dto.fit.FitSessionData;
import br.com.menthoros.backend.exception.FitParseException;
import br.com.menthoros.backend.services.FitParseService;
import com.garmin.fit.Decode;
import com.garmin.fit.FileIdMesgListener;
import com.garmin.fit.FitRuntimeException;
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

    @Override
    public FitSessionData parse(InputStream in) {
        List<FitLapData> laps = new ArrayList<>();
        AtomicLong serialNumber = new AtomicLong(0);
        AtomicInteger lapOrdem = new AtomicInteger(1);
        AtomicReference<FitSessionData> resultado = new AtomicReference<>();

        MesgBroadcaster broadcaster = new MesgBroadcaster();

        broadcaster.addListener((FileIdMesgListener) mesg -> {
            if (mesg.getSerialNumber() != null) {
                serialNumber.set(mesg.getSerialNumber());
            }
        });

        broadcaster.addListener((LapMesgListener) mesg -> laps.add(new FitLapData(
                lapOrdem.getAndIncrement(),
                duracaoDeSegundos(mesg.getTotalElapsedTime()),
                distanciaKmDeMetros(mesg.getTotalDistance()),
                inteiroOuNulo(mesg.getAvgHeartRate()),
                inteiroOuNulo(mesg.getMaxHeartRate())
        )));

        broadcaster.addListener((SessionMesgListener) mesg -> {
            Sport sport = mesg.getSport();
            boolean corrida = sport == Sport.RUNNING;
            Instant startInstant = mesg.getStartTime() != null
                    ? mesg.getStartTime().getDate().toInstant()
                    : Instant.now();

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
                    laps
            ));
        });

        try {
            new Decode().read(in, broadcaster);
        } catch (FitRuntimeException e) {
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
}
