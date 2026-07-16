package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
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
@Component
public class IntervalsIcuActivityMapper {

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

        treino.setFcMedia(roundToInt(dto.averageHeartrate()));
        treino.setFcMax(roundToInt(dto.maxHeartrate()));
        treino.setPaceMedia(calculatePace(dto.movingTimeSeg(), dto.distance(), dto.averageSpeed()));
        treino.setCadenciaMedia(sanitizeCadenciaIntervalsIcu(dto.averageCadence()));
        treino.setPercepcaoEsforco(dto.icuRpe() != null ? (int) Math.round(dto.icuRpe()) : null);
        treino.setElevacaoGanhoMetros(dto.totalElevationGain() != null ? (int) Math.round(dto.totalElevationGain()) : null);
        treino.setDeviceName(dto.deviceName());
        treino.setMetadadosSincronizacao(buildMetadadosSincronizacao(dto));

        return treino;
    }

    /**
     * Cadência do intervals.icu — unidade real (rpm/spm, perna única ou total) ainda NÃO
     * confirmada contra payload real. Isolada de propósito (não reaproveita
     * {@code convertStravaCadence}/{@code sanitizeCadence} do sync Strava por analogia — a fórmula
     * é diferente por fonte e não deve ser assumida igual sem validação). Revisitar no gate de
     * smoke (D6/tasks.md 7.1 item a) antes de confiar neste valor em produção.
     */
    private Integer sanitizeCadenciaIntervalsIcu(Double averageCadence) {
        return roundToInt(averageCadence);
    }

    private String buildMetadadosSincronizacao(IcuActivityDto dto) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"icuTrainingLoad\":").append(dto.icuTrainingLoad() != null ? dto.icuTrainingLoad() : "null").append(",");
        json.append("\"calories\":").append(dto.calories() != null ? dto.calories() : "null").append(",");
        json.append("\"totalElevationGain\":").append(dto.totalElevationGain() != null ? dto.totalElevationGain() : "null").append(",");
        json.append("\"deviceName\":").append(dto.deviceName() != null ? "\"" + dto.deviceName() + "\"" : "null");
        json.append("}");
        return json.toString();
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
