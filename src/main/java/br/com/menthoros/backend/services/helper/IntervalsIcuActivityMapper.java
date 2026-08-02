package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
import br.com.menthoros.backend.dto.intervalsicu.IcuActivityIntervalDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converte {@link IcuActivityDto} (atividade lida do intervals.icu) em {@link TreinoRealizado}.
 * <p>
 * Componente puro, sem IO. Recorte próprio de modalidades desta change (NÃO espelha
 * {@code StravaActivityServiceImpl.RUN_SPORT_TYPES}): {@code Run, TrailRun, VirtualRun,
 * Treadmill} — esteira entra porque é corrida para efeito de TSS/PMC mesmo sem GPS.
 * <p>
 * {@code duracaoMin} usa {@link Duration#ZERO} como sentinela de "ausente" (a coluna
 * {@code duracao_min} é {@code NOT NULL} — {@code TreinoBase.java:45} — {@code null} literal não é
 * representável). {@code distanciaKm} usa {@code null} literal (coluna nullable).
 */
@Slf4j
@Component
public class IntervalsIcuActivityMapper {

    /** Abaixo destes limiares o intervalo é lixo da fonte, não uma volta. Ver isDegenerado. */
    private static final int MIN_DURACAO_SEG = 5;
    private static final double MIN_DISTANCIA_METROS = 20d;

    private static final Set<String> MODALIDADES_SUPORTADAS = Set.of("Run", "TrailRun", "VirtualRun", "Treadmill");

    public boolean isModalidadeSuportada(String type) {
        return type != null && MODALIDADES_SUPORTADAS.contains(type);
    }

    public TreinoRealizado map(IcuActivityDto dto, Atleta atleta) {
        if (dto == null) {
            throw new IllegalArgumentException("IcuActivityDto não pode ser nulo");
        }
        if (atleta == null) {
            throw new IllegalArgumentException("Atleta não pode ser nulo");
        }
        if (!isModalidadeSuportada(dto.type())) {
            throw new DomainRuleViolationException(
                    "Modalidade não suportada para import intervals.icu: " + dto.type());
        }

        LocalDate dataTreino = parseDataTreino(dto.startDateLocal());

        TreinoRealizado treino = new TreinoRealizado();
        treino.setAtleta(atleta);
        treino.setTenantId(atleta.getAssessoria().getId());
        treino.setDataTreino(dataTreino);
        treino.setDiaSemana(dataTreino != null ? mapDiaSemana(dataTreino) : null);
        treino.setTipoTreino(inferTipoTreino(dto, atleta));
        treino.setDescricao(dto.name());

        treino.setFonteDados(FonteDados.INTERVALS_ICU);
        treino.setExternalId(dto.id());
        treino.setStatus(TreinoExecucaoStatus.REALIZADO);
        treino.setCriadoPor("INTERVALS_ICU");
        treino.setStatusSincronizacao(StatusSincronizacao.PENDENTE);
        treino.setSincronizadoEm(Instant.now());

        treino.setDuracaoMin(dto.movingTimeSeg() != null ? Duration.ofSeconds(dto.movingTimeSeg()) : Duration.ZERO);
        treino.setDistanciaKm(toKm(dto.distance()));
        treino.setElapsedTimeSeg(dto.elapsedTimeSeg());

        treino.setVelocidadeMedia(toKmh(dto.averageSpeed()));

        treino.setFcMedia(roundToInt(dto.averageHeartrate()));
        treino.setFcMax(roundToInt(dto.maxHeartrate()));
        treino.setPaceMedia(calculatePace(dto.movingTimeSeg(), dto.distance(), dto.averageSpeed()));
        treino.setCadenciaMedia(sanitizeCadenciaIntervalsIcu(dto.averageCadence()));
        treino.setPercepcaoEsforco(dto.icuRpe() != null ? (int) Math.round(dto.icuRpe()) : null);
        treino.setElevacaoGanhoMetros(dto.totalElevationGain() != null ? (int) Math.round(dto.totalElevationGain()) : null);
        treino.setDeviceName(dto.deviceName());
        treino.setMetadadosSincronizacao(buildMetadadosSincronizacao(dto));

        anexarEtapas(treino, dto);

        return treino;
    }

    /**
     * Converte {@code icu_intervals} em {@link EtapaRealizada} e anexa ao treino.
     *
     * <p>Fica dentro do {@code map} porque os intervalos chegam no mesmo payload da activity — não
     * há segunda chamada HTTP, então nada precisa atravessar o orquestrador. As etapas persistem
     * por {@code cascade = CascadeType.ALL} quando o treino é salvo.
     */
    private void anexarEtapas(TreinoRealizado treino, IcuActivityDto dto) {
        List<IcuActivityIntervalDto> intervalos = dto.intervalos();
        if (intervalos == null || intervalos.isEmpty()) {
            return;
        }

        int ordem = 0;
        for (IcuActivityIntervalDto intervalo : intervalos) {
            if (intervalo == null || isDegenerado(intervalo)) {
                continue;
            }
            EtapaRealizada etapa = mapEtapa(intervalo, ++ordem);
            etapa.setTreinoRealizado(treino);
            treino.getEtapasRealizadas().add(etapa);
        }

        if (dto.lapCount() != null && dto.lapCount() != ordem) {
            log.debug("Contagem de etapas divergiu de icu_lap_count: mapeadas={}, icu_lap_count={}, activityId={}",
                    ordem, dto.lapCount(), dto.id());
        }
    }

    /**
     * O payload real traz intervalos-lixo — o smoke encontrou um de 1 s e 2,4 m, que
     * {@code icu_lap_count} não conta. Persistido, ele entraria nos cálculos de drift de FC e
     * progressão de pace das skills como se fosse uma volta.
     *
     * <p>Limiares conservadores de propósito: o tiro legítimo mais curto que um treinador prescreve
     * (strides de 15–20 s) fica com folga acima.
     */
    private boolean isDegenerado(IcuActivityIntervalDto intervalo) {
        int movingTime = intervalo.movingTimeSeg() != null ? intervalo.movingTimeSeg() : 0;
        double distancia = intervalo.distance() != null ? intervalo.distance() : 0d;
        return movingTime < MIN_DURACAO_SEG || distancia < MIN_DISTANCIA_METROS;
    }

    private EtapaRealizada mapEtapa(IcuActivityIntervalDto intervalo, int ordem) {
        EtapaRealizada etapa = new EtapaRealizada();

        // ordem e splitIndex vêm da POSIÇÃO: o `id` do intervals.icu é opaco e não ordena nada.
        etapa.setOrdem(ordem);
        etapa.setSplitIndex(ordem);
        etapa.setTipoEtapa(mapTipoEtapa(intervalo.type()));
        etapa.setDescricao(intervalo.label() != null && !intervalo.label().isBlank()
                ? intervalo.label()
                : "Lap " + ordem);

        // duracao SEMPRE de moving_time: elapsed_time incluiria tempo parado, que o
        // TssCalculatorService, o tempo em zona e o decoupling leem como tempo de treino.
        Duration duracao = intervalo.movingTimeSeg() != null
                ? Duration.ofSeconds(intervalo.movingTimeSeg())
                : null;
        etapa.setDuracao(duracao);
        etapa.setTempoMovimento(duracao);

        etapa.setDistanciaKm(toKmEscala3(intervalo.distance()));
        etapa.setFcMedia(roundToInt(intervalo.averageHeartrate()));
        etapa.setFcMax(roundToInt(intervalo.maxHeartrate()));
        etapa.setVelocidadeMedia(toBigDecimal(toKmh(intervalo.averageSpeed()), 2));
        etapa.setPaceMedia(calculatePace(intervalo.movingTimeSeg(), intervalo.distance(), intervalo.averageSpeed()));
        etapa.setCadenciaMedia(sanitizeCadenciaIntervalsIcu(intervalo.averageCadence()));
        etapa.setPotenciaMedia(roundToInt(intervalo.averageWatts()));

        // A fonte não expõe perda de elevação por intervalo — deixar null, não zerar.
        etapa.setElevacaoGanhoMetros(roundToInt(intervalo.totalElevationGain()));

        etapa.setPassadaMediaM(toBigDecimal(intervalo.averageStride(), 2));
        etapa.setGctMedioMs(roundToInt(intervalo.averageStanceTime()));
        etapa.setGctEquilibrioPct(toBigDecimal(intervalo.averageStanceTimeBalance(), 1));
        etapa.setOscilacaoVerticalCm(toBigDecimal(mmParaCm(intervalo.averageVerticalOscillation()), 1));
        etapa.setProporcaoVerticalPct(toBigDecimal(intervalo.averageVerticalRatio(), 1));
        etapa.setTemperaturaMediaC(toBigDecimal(intervalo.averageTemp(), 1));

        etapa.setZona(intervalo.zone());
        etapa.setIntensidadePct(toBigDecimal(intervalo.intensity(), 2));
        etapa.setInclinacaoMediaPct(toBigDecimal(fracaoParaPercentual(intervalo.averageGradient()), 1));

        return etapa;
    }

    /** {@code average_vertical_oscillation} vem em milímetros; a coluna é em centímetros. */
    private Double mmParaCm(Double milimetros) {
        return milimetros != null ? milimetros / 10d : null;
    }

    /** {@code average_gradient} vem em fração ({@code 0.0011977} = 0,1%). */
    private Double fracaoParaPercentual(Double fracao) {
        return fracao != null ? fracao * 100d : null;
    }

    private String mapTipoEtapa(String type) {
        if (type == null) {
            return null;
        }
        return switch (type.toUpperCase()) {
            case "WORK" -> "PRINCIPAL";
            case "RECOVERY" -> "RECUPERACAO";
            case "WARMUP" -> "AQUECIMENTO";
            case "COOLDOWN" -> "DESAQUECIMENTO";
            default -> null;
        };
    }

    private BigDecimal toKmEscala3(Double meters) {
        if (meters == null) {
            return null;
        }
        return BigDecimal.valueOf(meters / 1000d).setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal toBigDecimal(Double valor, int escala) {
        if (valor == null) {
            return null;
        }
        return BigDecimal.valueOf(valor).setScale(escala, RoundingMode.HALF_UP);
    }

    /**
     * Cadência do intervals.icu — CONFIRMADO contra payload real (smoke do gate 3.0, 2026-07-16,
     * atleta i641775, activity `i166338796`, Garmin Forerunner 970): {@code average_cadence} vem
     * em passos/min de UMA perna, igual à convenção do Strava/FIT — valor real observado
     * {@code 80.82...}, plausível como cadência de perna única para um ritmo de 6:29/km (dobrado:
     * ~161.6 spm total, faixa normal de corrida). Isolada de propósito (não chama
     * {@code convertStravaCadence}/{@code sanitizeCadence} do Strava diretamente — mesma fórmula,
     * mas fonte e sanitização isoladas para não acoplar as duas integrações).
     */
    private Integer sanitizeCadenciaIntervalsIcu(Double averageCadence) {
        if (averageCadence == null) {
            return null;
        }
        int cadenciaTotal = (int) Math.round(averageCadence * 2d);
        if (cadenciaTotal < 60 || cadenciaTotal > 200) {
            return null;
        }
        return cadenciaTotal;
    }

    private static final ObjectMapper METADADOS_MAPPER = new ObjectMapper();

    /**
     * {@code deviceName} vem do intervals.icu (terceiro) — usa Jackson em vez de concatenação de
     * string (achado do QA gate, 2026-07-16): um valor contendo aspas ou barra invertida quebrava
     * o JSON armazenado.
     */
    private String buildMetadadosSincronizacao(IcuActivityDto dto) {
        Map<String, Object> metadados = new LinkedHashMap<>();
        metadados.put("icuTrainingLoad", dto.icuTrainingLoad());
        metadados.put("calories", dto.calories());
        metadados.put("totalElevationGain", dto.totalElevationGain());
        metadados.put("deviceName", dto.deviceName());
        try {
            return METADADOS_MAPPER.writeValueAsString(metadados);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar metadadosSincronizacao", e);
        }
    }

    /**
     * Pace primariamente de {@code moving_time}/{@code distance} (mesmo método do
     * {@code StravaActivityServiceImpl}, linhas ~513-519) — {@code average_speed} só como fallback
     * quando os primeiros faltarem. Não inverter a prioridade.
     */
    private Duration calculatePace(Integer movingTimeSeg, Double distanceMeters, Double averageSpeedMs) {
        if (movingTimeSeg != null && movingTimeSeg > 0 && distanceMeters != null && distanceMeters > 0d) {
            double distanceKm = distanceMeters / 1000d;
            long secondsPerKm = Math.round(movingTimeSeg / distanceKm);
            return Duration.ofSeconds(Math.max(secondsPerKm, 0));
        }
        if (averageSpeedMs != null && averageSpeedMs > 0d) {
            long secondsPerKm = Math.round(1000d / averageSpeedMs);
            return Duration.ofSeconds(Math.max(secondsPerKm, 0));
        }
        return null;
    }

    private BigDecimal toKm(Double meters) {
        if (meters == null) {
            return null;
        }
        return BigDecimal.valueOf(meters / 1000d).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * {@code average_speed} do intervals.icu vem em m/s, mesma convenção do Strava —
     * {@code treino.velocidadeMedia} é km/h (achado do QA gate, 2026-07-16: um valor tinha sido
     * atribuído direto sem converter, ~3.6x menor que o real). Isolada de propósito, mesmo padrão
     * de {@code sanitizeCadenciaIntervalsIcu} (não chama {@code StravaActivityServiceImpl.toKmh}
     * diretamente).
     */
    private Double toKmh(Double metersPerSecond) {
        if (metersPerSecond == null) {
            return null;
        }
        return BigDecimal.valueOf(metersPerSecond * 3.6d).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private Integer roundToInt(Double value) {
        return value != null ? (int) Math.round(value) : null;
    }

    /**
     * {@code tipoTreino} não está no escopo explícito do D2 (a activity do intervals.icu não traz
     * um campo equivalente ao {@code workout_type} do Strava). Reaproveita a heurística de duração
     * do fallback do {@code StravaActivityServiceImpl.inferTipoTreino} (mesmos limiares), já
     * validada em produção, em vez de inventar uma nova regra não testada.
     */
    private TipoTreino inferTipoTreino(IcuActivityDto dto, Atleta atleta) {
        int movingTime = dto.movingTimeSeg() != null ? dto.movingTimeSeg() : 0;
        Integer fcMedia = roundToInt(dto.averageHeartrate());
        Integer limiar = atleta.getFcLimiarCalculada();

        if (movingTime >= 5400) {
            return TipoTreino.LONGO;
        }
        if (fcMedia != null && limiar != null && fcMedia >= limiar) {
            return TipoTreino.TEMPO_RUN;
        }
        if (movingTime <= 1800) {
            return TipoTreino.FACIL;
        }
        return TipoTreino.CONTINUO;
    }

    private DiaSemana mapDiaSemana(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> DiaSemana.SEGUNDA;
            case TUESDAY -> DiaSemana.TERCA;
            case WEDNESDAY -> DiaSemana.QUARTA;
            case THURSDAY -> DiaSemana.QUINTA;
            case FRIDAY -> DiaSemana.SEXTA;
            case SATURDAY -> DiaSemana.SABADO;
            case SUNDAY -> DiaSemana.DOMINGO;
        };
    }

    /**
     * Preserva a data LOCAL do payload sem conversão de fuso do servidor (pre-mortem #4):
     * {@code start_date_local} sem offset vira {@link LocalDateTime#toLocalDate()} direto; com
     * offset, {@link OffsetDateTime#toLocalDate()} também ignora o offset e preserva a data
     * escrita — nenhum dos dois caminhos passa por {@code ZoneId.systemDefault()} (diferente do
     * parsing do Strava, que faz esse round-trip; aqui é evitado de propósito para não arriscar
     * uma virada de dia por DST do servidor).
     */
    private LocalDate parseDataTreino(String startDateLocal) {
        if (startDateLocal == null || startDateLocal.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(startDateLocal).toLocalDate();
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(startDateLocal).toLocalDate();
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    return Instant.parse(startDateLocal).atZone(java.time.ZoneOffset.UTC).toLocalDate();
                } catch (DateTimeParseException ignoredThird) {
                    return null;
                }
            }
        }
    }
}
