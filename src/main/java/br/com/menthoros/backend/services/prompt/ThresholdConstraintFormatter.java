package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.enums.ConfiancaInferencia;
import br.com.menthoros.backend.services.helper.ThresholdInferenceService;
import br.com.menthoros.backend.services.prompt.constraint.Constraint;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
public class ThresholdConstraintFormatter {

    /**
     * Idempotent: YES · Side Effects: NONE · Tenant-aware: NO
     */
    public Optional<Constraint> constraintFc(PlanoMetaDados metaDados, Atleta atleta) {
        if (metaDados == null || metaDados.getFcLimiarEstimado() == null) return Optional.empty();
        if (!fcLimiarDesatualizado(atleta)) return Optional.empty();

        Integer fc = metaDados.getFcLimiarEstimado();
        ConfiancaInferencia confianca = metaDados.getConfiancaInferenciaFc();
        LocalDate dataInferencia = metaDados.getDataInferenciaLimiar();

        String descricao = formatarDescricaoFc(fc, confianca, dataInferencia);
        return Optional.of(Constraint.limiarFcEstimado(descricao));
    }

    /**
     * Idempotent: YES · Side Effects: NONE · Tenant-aware: NO
     */
    public Optional<Constraint> constraintPace(PlanoMetaDados metaDados, Atleta atleta) {
        if (metaDados == null || metaDados.getPaceLimiarEstimado() == null) return Optional.empty();
        if (!paceLimiarDesatualizado(atleta)) return Optional.empty();

        BigDecimal pace = metaDados.getPaceLimiarEstimado();
        ConfiancaInferencia confianca = metaDados.getConfiancaInferenciaPace();
        LocalDate dataInferencia = metaDados.getDataInferenciaLimiar();

        String descricao = formatarDescricaoPace(pace, confianca, dataInferencia);
        return Optional.of(Constraint.limiarPaceEstimado(descricao));
    }

    private String formatarDescricaoFc(Integer fc, ConfiancaInferencia confianca, LocalDate dataInferencia) {
        String dataStr = dataInferencia != null ? dataInferencia.toString() : "data desconhecida";
        String nivel = confianca != null ? confianca.name() : "DESCONHECIDA";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "[LIMIAR_FC_ESTIMADO] FC limiar inferido: %d bpm (inferido em %s) | Confiança: %s",
                fc, dataStr, nivel));
        sb.append(" | Fonte: mediana dos 20%% maiores FC em treinos >20min dos últimos 30d.");
        sb.append(" ATENÇÃO: valor estimado — zonas derivadas são aproximadas. Recomendar teste formal.");
        if (ConfiancaInferencia.BAIXA.equals(confianca)) {
            sb.append(" CONFIANÇA BAIXA (poucos dados) — ampliar margem em ±5 bpm nas prescrições.");
        }
        return sb.toString();
    }

    private String formatarDescricaoPace(BigDecimal pace, ConfiancaInferencia confianca, LocalDate dataInferencia) {
        String dataStr = dataInferencia != null ? dataInferencia.toString() : "data desconhecida";
        String nivel = confianca != null ? confianca.name() : "DESCONHECIDA";
        String paceFormatado = ThresholdInferenceService.formatarPace(pace);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "[LIMIAR_PACE_ESTIMADO] Pace limiar inferido: %s (inferido em %s) | Confiança: %s",
                paceFormatado, dataStr, nivel));
        sb.append(" | Fonte: mediana dos 20%% paces mais rápidos em treinos contínuos >20min dos últimos 30d.");
        sb.append(" ATENÇÃO: valor estimado — usar com margem de ±5s/km nas prescrições.");
        if (ConfiancaInferencia.BAIXA.equals(confianca)) {
            sb.append(" CONFIANÇA BAIXA (poucos dados) — ampliar margem em ±10s/km.");
        }
        return sb.toString();
    }

    private boolean fcLimiarDesatualizado(Atleta atleta) {
        if (atleta == null) return false;
        if (atleta.getFcLimiar() == null || atleta.getDataUltimoTesteFc() == null) return true;
        return ChronoUnit.DAYS.between(atleta.getDataUltimoTesteFc(), LocalDate.now())
                > ThresholdInferenceService.DIAS_LIMIAR_DESATUALIZACAO;
    }

    private boolean paceLimiarDesatualizado(Atleta atleta) {
        if (atleta == null) return false;
        if (atleta.getPaceLimiar() == null || atleta.getDataUltimoTestePace() == null) return true;
        return ChronoUnit.DAYS.between(atleta.getDataUltimoTestePace(), LocalDate.now())
                > ThresholdInferenceService.DIAS_LIMIAR_DESATUALIZACAO;
    }
}
