package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.workout.HrTarget;
import br.com.menthoros.backend.domain.workout.PaceTarget;
import br.com.menthoros.backend.dto.output.TreinoHojeDto.AlvoPrimario;
import br.com.menthoros.backend.dto.output.TreinoHojeDto.EtapaAlvoDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.EtapaTreino;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolve o alvo efetivo de uma etapa para a tela do atleta com a mesma precedência do
 * {@link IntervalsIcuWorkoutConverter#stepDeEtapa}: FC resolvida vence e o pace desce para o
 * texto; FC descartada por falta de limiar deixa o pace assumir e vai ela para o texto; sem
 * nenhum dos dois, {@code NENHUM}. Se a precedência mudar lá, tem de mudar aqui — o teste
 * compara os dois contra o mesmo {@code WorkoutStep}.
 *
 * <p><b>Idempotente:</b> YES — cálculo puro.
 * <p><b>Side Effects:</b> NONE.
 * <p><b>Tenant-aware:</b> NO — opera sobre o atleta que o chamador já resolveu.
 */
@Component
@RequiredArgsConstructor
public class EtapaAlvoResolver {

    private final IntervalsIcuFcAlvoResolver fcAlvoResolver;

    public EtapaAlvoDto resolver(EtapaTreino etapa, Atleta atleta) {
        IntervalsIcuFcAlvoResolver.Resolucao fc = fcAlvoResolver.resolver(
                IntervalsIcuTargetParser.parseFc(etapa.getFcAlvoEtapa()).orElse(null), atleta);
        PaceTarget pace = IntervalsIcuTargetParser.parsePace(etapa.getRitmoAlvo()).orElse(null);

        AlvoPrimario primario;
        Integer fcMin = null;
        Integer fcMax = null;
        String paceAlvo = null;
        String secundario = null;

        if (fc.alvo() != null) {
            HrTarget alvo = fc.alvo();
            primario = AlvoPrimario.FC;
            fcMin = alvo.startBpm();
            fcMax = alvo.endBpm();
            if (pace != null) {
                secundario = etapa.getRitmoAlvo().trim();
            }
        } else {
            if (fc.descartadoPorFaltaDeDado()) {
                secundario = etapa.getFcAlvoEtapa().trim();
            }
            if (pace != null) {
                primario = AlvoPrimario.PACE;
                paceAlvo = formatarPace(pace);
            } else {
                primario = AlvoPrimario.NENHUM;
            }
        }

        return new EtapaAlvoDto(
                etapa.getOrdem(),
                etapa.getTipoEtapa(),
                etapa.getDescricaoEtapa(),
                etapa.getDuracaoMin(),
                etapa.getDistanciaKm() != null ? etapa.getDistanciaKm().doubleValue() : null,
                etapa.getBlocoId(),
                etapa.getBlocoRepeticoes(),
                primario, fcMin, fcMax, paceAlvo, secundario);
    }

    private static String formatarPace(PaceTarget pace) {
        String inicio = mmss(pace.startSecsPerKm());
        if (pace.startSecsPerKm().equals(pace.endSecsPerKm())) {
            return inicio;
        }
        return inicio + "-" + mmss(pace.endSecsPerKm());
    }

    private static String mmss(int segundos) {
        return String.format("%d:%02d", segundos / 60, segundos % 60);
    }
}
