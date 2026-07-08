package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.DecisaoProgressao;
import br.com.menthoros.backend.dto.ProgressaoHistoricoResumo;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.EstadoProgressao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.PlanoMetadadosService;
import br.com.menthoros.backend.services.ProgressaoTreinoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressaoTreinoServiceImpl implements ProgressaoTreinoService {

    private static final Set<TipoTreino> TREINOS_DUROS = Set.of(
            TipoTreino.INTERVALADO, TipoTreino.TIRO, TipoTreino.TEMPO_RUN, TipoTreino.SUBIDA
    );

    private static final double THRESHOLD_ADERENCIA_PROGREDIR = 0.80;
    private static final double THRESHOLD_ADERENCIA_PROGREDIR_LEVE = 0.70;
    private static final double THRESHOLD_ADERENCIA_REDUZIR = 0.60;
    private static final double THRESHOLD_TSB_PROGREDIR = -15.0;
    private static final double THRESHOLD_TSB_REDUZIR = -22.0;
    private static final double THRESHOLD_RPE_LIMITE = 7.5;
    private static final double THRESHOLD_RPE_REDUZIR = 8.5;
    private static final int LONGAS_MINIMAS_PROGREDIR = 2;
    private static final int TREINOS_MINIMOS_21D = 3;

    private static final double AJUSTE_VOLUME_PROGREDIR      =  0.06;
    private static final double AJUSTE_VOLUME_PROGREDIR_LEVE =  0.03;
    private static final double AJUSTE_VOLUME_REDUZIR        = -0.05;
    private static final int    AJUSTE_LONGO_PROGREDIR       =  10;
    private static final int    AJUSTE_LONGO_PROGREDIR_LEVE  =   5;
    private static final int    AJUSTE_LONGO_REDUZIR         = -10;

    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final PlanoMetadadosService planoMetadadosService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public ProgressaoHistoricoResumo calcularHistorico(UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        LocalDate hoje = LocalDate.now(clock);
        LocalDate inicio7d = hoje.minusDays(7);
        LocalDate inicio21d = hoje.minusDays(21);
        LocalDate inicio42d = hoje.minusDays(42);

        List<TreinoRealizado> treinos42d = treinoRealizadoRepository
                .findByAtletaIdAndTenantIdAndDataTreinoBetween(atletaId, tenantId, inicio42d, hoje);

        List<TreinoRealizado> treinos21d = treinos42d.stream()
                .filter(t -> !t.getDataTreino().isBefore(inicio21d))
                .toList();

        List<TreinoRealizado> treinos7d = treinos42d.stream()
                .filter(t -> !t.getDataTreino().isBefore(inicio7d))
                .toList();

        double volumeKm42d = calcularVolumeKm(treinos42d);
        double volumeKm21d = calcularVolumeKm(treinos21d);
        double volumeKm7d = calcularVolumeKm(treinos7d);

        int longoesRealizados21d = contarLongoes(treinos21d);
        int longoesRealizados7d = contarLongoes(treinos7d);

        Double rpeMedioTreinosDuros = calcularRpeMedioTreinosDuros(treinos21d);

        int treinosConcluidos21d = treinos21d.size();
        List<TreinoPlanejado> planejados = treinoPlanejadoRepository
                .findComRealizadoByAtletaAndPeriodo(atletaId, tenantId, inicio21d);
        int treinosPlanejados21d = planejados.size();

        PlanoMetaDados metaDados = planoMetadadosService.buscarPorAtletaId(atletaId);

        log.debug("Histórico calculado para atleta {}: concluidos21d={}, planejados21d={}, tsb={}",
                atletaId, treinosConcluidos21d, treinosPlanejados21d, metaDados.getTsbAtual());

        return new ProgressaoHistoricoResumo(
                treinosConcluidos21d, treinosPlanejados21d,
                volumeKm7d, volumeKm21d, volumeKm42d,
                longoesRealizados7d, longoesRealizados21d,
                rpeMedioTreinosDuros,
                metaDados.getTsbAtual(), metaDados.getCtlAtual(), metaDados.getAtlAtual(),
                metaDados.getSemanasProgressaoContinua() != null ? metaDados.getSemanasProgressaoContinua() : 0
        );
    }

    @Override
    public DecisaoProgressao calcularDecisao(ProgressaoHistoricoResumo resumo) {
        if (resumo.treinosConcluidos21d() < TREINOS_MINIMOS_21D) {
            log.debug("Histórico insuficiente ({} treinos em 21d) — retornando MANTER", resumo.treinosConcluidos21d());
            return new DecisaoProgressao(EstadoProgressao.MANTER, 0.0, 0, false, "histórico insuficiente");
        }

        double aderencia = calcularAderencia(resumo);
        double tsb = resumo.tsbAtual() != null ? resumo.tsbAtual() : 0.0;
        Double rpe = resumo.rpeMedioTreinosDuros();

        if (deveReduzir(aderencia, tsb, rpe)) {
            String motivo = motivoReducao(aderencia, tsb, rpe);
            log.info("DecisaoProgressao REDUZIR para histórico — motivo: {}", motivo);
            return new DecisaoProgressao(EstadoProgressao.REDUZIR, AJUSTE_VOLUME_REDUZIR, AJUSTE_LONGO_REDUZIR, false, motivo);
        }

        if (podeProgredir(aderencia, tsb, rpe, resumo.longoesRealizados21d())) {
            log.info("DecisaoProgressao PROGREDIR — aderência={}, longões21d={}, TSB={}",
                    aderencia, resumo.longoesRealizados21d(), tsb);
            return new DecisaoProgressao(EstadoProgressao.PROGREDIR, AJUSTE_VOLUME_PROGREDIR, AJUSTE_LONGO_PROGREDIR, true,
                    "atleta respondendo bem ao treino");
        }

        if (podeProgredirLeve(aderencia, tsb)) {
            log.info("DecisaoProgressao PROGREDIR_LEVE — aderência={}, TSB={}", aderencia, tsb);
            return new DecisaoProgressao(EstadoProgressao.PROGREDIR_LEVE, AJUSTE_VOLUME_PROGREDIR_LEVE, AJUSTE_LONGO_PROGREDIR_LEVE, false,
                    "progressão moderada recomendada");
        }

        log.debug("DecisaoProgressao MANTER — aderência={}, TSB={}", aderencia, tsb);
        return new DecisaoProgressao(EstadoProgressao.MANTER, 0.0, 0, false, "manter volume atual");
    }

    private double calcularAderencia(ProgressaoHistoricoResumo resumo) {
        if (resumo.treinosPlanejados21d() == 0) return 0.0;
        return (double) resumo.treinosConcluidos21d() / resumo.treinosPlanejados21d();
    }

    private boolean deveReduzir(double aderencia, double tsb, Double rpe) {
        return tsb < THRESHOLD_TSB_REDUZIR
                || aderencia < THRESHOLD_ADERENCIA_REDUZIR
                || (rpe != null && rpe > THRESHOLD_RPE_REDUZIR);
    }

    private boolean podeProgredir(double aderencia, double tsb, Double rpe, int longoes21d) {
        return aderencia >= THRESHOLD_ADERENCIA_PROGREDIR
                && longoes21d >= LONGAS_MINIMAS_PROGREDIR
                && tsb > THRESHOLD_TSB_PROGREDIR
                && (rpe == null || rpe <= THRESHOLD_RPE_LIMITE);
    }

    private boolean podeProgredirLeve(double aderencia, double tsb) {
        return aderencia >= THRESHOLD_ADERENCIA_PROGREDIR_LEVE
                && tsb > THRESHOLD_TSB_REDUZIR;
    }

    private String motivoReducao(double aderencia, double tsb, Double rpe) {
        if (tsb < THRESHOLD_TSB_REDUZIR) return String.format("TSB crítico (%.1f)", tsb);
        if (aderencia < THRESHOLD_ADERENCIA_REDUZIR) return "aderência abaixo de 60%";
        return String.format("RPE médio elevado (%.1f)", rpe);
    }

    private double calcularVolumeKm(List<TreinoRealizado> treinos) {
        return treinos.stream()
                .filter(t -> t.getDistanciaKm() != null)
                .mapToDouble(t -> t.getDistanciaKm().doubleValue())
                .sum();
    }

    private int contarLongoes(List<TreinoRealizado> treinos) {
        return (int) treinos.stream()
                .filter(t -> TipoTreino.LONGO.equals(t.getTipoTreino()))
                .count();
    }

    private Double calcularRpeMedioTreinosDuros(List<TreinoRealizado> treinos) {
        OptionalDouble media = treinos.stream()
                .filter(t -> TREINOS_DUROS.contains(t.getTipoTreino()))
                .filter(t -> t.getPercepcaoEsforco() != null)
                .mapToInt(TreinoRealizado::getPercepcaoEsforco)
                .average();
        return media.isPresent() ? media.getAsDouble() : null;
    }
}
