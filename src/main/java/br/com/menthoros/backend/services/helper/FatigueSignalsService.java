package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.config.core.ReadinessProperties;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.FatigueSignalType;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.NivelProntidao;
import br.com.menthoros.backend.enums.TipoTreino;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Lista <b>todos</b> os sinais de fadiga do atleta para a semana que será gerada.
 *
 * <p>Por que não reusar a recomendação de {@link IntervaladoElegibilidadeService}: ela retorna no
 * primeiro portão que dispara, então um atleta com TSB baixo <i>e</i> recuperação insuficiente
 * aparece com um sinal só — e a regra de cobertura precisa do conjunto para decidir em quais dias o
 * descanso é legítimo (add-descanso-explicito-por-fadiga, Decisão 3). Os limiares são os mesmos
 * ({@link FatigueThresholds}); a degradação de intensidade continua sendo daquele serviço.</p>
 *
 * <p>Idempotent: YES — leitura pura sobre os dados recebidos. Side Effects: NONE (só log).
 * Tenant-aware: NO — recebe o atleta já resolvido pelo tenant.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FatigueSignalsService {

    private final ReadinessProperties readinessProperties;

    /**
     * Avalia os sinais, cada um de forma independente.
     *
     * @param atleta               atleta (nível define os limiares; {@code null} cai em Intermediário)
     * @param metaDados            TSB de prontidão, CTL e alerta de dias consecutivos
     * @param treinosUltimas4Semanas histórico já carregado (RPE e último intensivo)
     * @param dataReferencia       data base da avaliação
     * @param nivelProntidaoHoje   check-in do dia, ou {@code null} quando não houver
     * @param maxDiasConsecutivos  máximo de dias consecutivos recomendado ao atleta (vira o limiar
     *                             citado no motivo do descanso)
     * @return sinais detectados, em ordem estável; lista vazia quando não há fadiga
     */
    public List<FatigueSignal> avaliar(@Nullable Atleta atleta,
                                       @Nullable PlanoMetaDados metaDados,
                                       @Nullable List<TreinoRealizado> treinosUltimas4Semanas,
                                       LocalDate dataReferencia,
                                       @Nullable NivelProntidao nivelProntidaoHoje,
                                       int maxDiasConsecutivos) {
        NivelExperiencia nivel = atleta != null && atleta.getNivelExperiencia() != null
                ? atleta.getNivelExperiencia()
                : NivelExperiencia.INTERMEDIARIO;

        List<FatigueSignal> sinais = new ArrayList<>();

        Double tsb = metaDados != null ? metaDados.getTsbProntidaoAtual() : null;
        double tsbLimiar = FatigueThresholds.tsb(nivel);
        if (tsb != null && tsb < tsbLimiar) {
            sinais.add(FatigueSignal.de(FatigueSignalType.TSB_BAIXO, tsb, tsbLimiar));
        }

        double rpeMedia = rpeMedia7Dias(treinosUltimas4Semanas, dataReferencia);
        if (rpeMedia >= FatigueThresholds.RPE_MEDIO_7D) {
            sinais.add(FatigueSignal.de(FatigueSignalType.RPE_ALTO, rpeMedia, FatigueThresholds.RPE_MEDIO_7D));
        }

        long minHoras = FatigueThresholds.horasRecuperacao(nivel);
        ultimoTreinoIntensivo(treinosUltimas4Semanas, dataReferencia).ifPresent(ultimo -> {
            long horas = ChronoUnit.HOURS.between(
                    ultimo.getDataTreino().atStartOfDay(), dataReferencia.atStartOfDay());
            if (horas < minHoras) {
                sinais.add(FatigueSignal.de(FatigueSignalType.RECUPERACAO_INSUFICIENTE, horas, minHoras));
            }
        });

        if (metaDados != null && Boolean.TRUE.equals(metaDados.getAlertaDiasConsecutivos())) {
            int dias = metaDados.getDiasConsecutivosTreino() != null ? metaDados.getDiasConsecutivosTreino() : 0;
            sinais.add(FatigueSignal.de(FatigueSignalType.DIAS_CONSECUTIVOS_LIMITE, dias, maxDiasConsecutivos));
        }

        if (readinessProperties.isEnabled() && nivelProntidaoHoje == NivelProntidao.DESCANSAR) {
            sinais.add(FatigueSignal.de(FatigueSignalType.READINESS_DESCANSAR));
        }

        Double ctl = metaDados != null ? metaDados.getCtlAtual() : null;
        double ctlMinimo = FatigueThresholds.ctlMinimo(nivel);
        if (ctl != null && ctl < ctlMinimo) {
            sinais.add(FatigueSignal.de(FatigueSignalType.CTL_BAIXO, ctl, ctlMinimo));
        }

        if (!sinais.isEmpty()) {
            log.debug("SINAIS DE FADIGA: {}", sinais.stream().map(s -> s.type().name()).toList());
        }
        return List.copyOf(sinais);
    }

    /** Média de RPE dos treinos nos 7 dias anteriores à referência; 0 quando não há dado. */
    private double rpeMedia7Dias(@Nullable List<TreinoRealizado> treinos, LocalDate dataReferencia) {
        if (treinos == null || treinos.isEmpty()) return 0.0;
        LocalDate limite = dataReferencia.minusDays(7);
        return treinos.stream()
                .filter(t -> t.getDataTreino() != null && !t.getDataTreino().isBefore(limite))
                .filter(t -> t.getPercepcaoEsforco() != null)
                .mapToInt(TreinoRealizado::getPercepcaoEsforco)
                .average()
                .orElse(0.0);
    }

    /** Último INTERVALADO/TIRO antes da referência — mesma definição do portão de recuperação. */
    private Optional<TreinoRealizado> ultimoTreinoIntensivo(@Nullable List<TreinoRealizado> treinos,
                                                            LocalDate dataReferencia) {
        if (treinos == null || treinos.isEmpty()) return Optional.empty();
        return treinos.stream()
                .filter(t -> t.getTipoTreinoEfetivo() == TipoTreino.INTERVALADO
                        || t.getTipoTreinoEfetivo() == TipoTreino.TIRO)
                .filter(t -> t.getDataTreino() != null && t.getDataTreino().isBefore(dataReferencia))
                .max(Comparator.comparing(TreinoRealizado::getDataTreino));
    }
}
