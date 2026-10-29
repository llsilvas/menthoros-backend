package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.AthleteTrainingZonesOutputDto;
import br.com.menthoros.backend.dto.output.AthleteTrainingZonesOutputDto.AjusteFadiga;
import br.com.menthoros.backend.dto.output.AthleteTrainingZonesOutputDto.Ancoras;
import br.com.menthoros.backend.dto.output.AthleteTrainingZonesOutputDto.Disponibilidade;
import br.com.menthoros.backend.dto.output.AthleteTrainingZonesOutputDto.FcFaixa;
import br.com.menthoros.backend.dto.output.AthleteTrainingZonesOutputDto.FcMaxima;
import br.com.menthoros.backend.dto.output.AthleteTrainingZonesOutputDto.PaceFaixa;
import br.com.menthoros.backend.dto.output.AthleteTrainingZonesOutputDto.ZonaDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.services.CoachAthleteZonesService;
import br.com.menthoros.backend.services.helper.PaceZoneCalculator;
import br.com.menthoros.backend.services.helper.ZonaTreinoService;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaCompleta;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Idempotent: YES — leitura pura. Side Effects: NONE. Tenant-aware: YES.
 *
 * <p>Não recalcula zona: as faixas vêm de {@link ZonaTreinoService}, as mesmas que o prompt da IA
 * recebe. O ajuste por fadiga é só informado — as faixas ficam na base.
 */
@Service
@RequiredArgsConstructor
public class CoachAthleteZonesServiceImpl implements CoachAthleteZonesService {

    private static final String MODELO = "LTHR_FRIEL";
    private static final String MOTIVO_SEM_PACE = "SEM_PACE_LIMIAR";
    private static final String MOTIVO_FC_Z6 = "FC_NAO_ESTABILIZA";
    private static final int ZONA_SPRINT = 6;

    private final AtletaRepository atletaRepository;
    private final PlanoMetadadosRepository planoMetadadosRepository;
    private final ZonaTreinoService zonaTreinoService;
    private final PaceZoneCalculator paceZoneCalculator;

    @Override
    @Transactional(readOnly = true)
    public AthleteTrainingZonesOutputDto buscarZonas(UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado"));

        BigDecimal paceLimiar = atleta.getPaceLimiar();
        boolean temPace = paceLimiar != null && paceLimiar.signum() > 0;
        Double tsb = planoMetadadosRepository.findLatestByAtletaIdAndTenantId(atletaId, tenantId)
                .map(PlanoMetaDados::getTsbProntidaoAtual).orElse(null);

        int ajuste = temPace
                ? paceZoneCalculator.calcularZonasAjustadasPorTsbProntidao(paceLimiar, tsb).ajusteSegundos()
                : 0;

        List<ZonaDto> zonas = zonaTreinoService.calcularZonas(atleta).stream()
                .map(z -> toDto(z, temPace))
                .toList();

        boolean fcLimiarEstimada = atleta.getFcLimiar() == null;
        return new AthleteTrainingZonesOutputDto(
                MODELO,
                new Ancoras(temPace ? paceLimiar : null, atleta.getDataUltimoTestePace(),
                        atleta.getFcLimiarCalculada(), atleta.getDataUltimoTesteFc(), fcLimiarEstimada),
                new AjusteFadiga(ajuste, tsb),
                new FcMaxima(atleta.getFcMaximaComFallback(),
                        atleta.getFcMaxima() == null || atleta.getFcMaxima() <= 0),
                new Disponibilidade(temPace, true, temPace ? null : MOTIVO_SEM_PACE),
                zonas);
    }

    private static ZonaDto toDto(ZonaCompleta z, boolean temPace) {
        PaceFaixa pace = temPace
                ? new PaceFaixa(segundos(z.pace().paceMin()), segundos(z.pace().paceMax()))
                : null;
        boolean sprint = z.numero() == ZONA_SPRINT;
        FcFaixa fc = sprint ? null : new FcFaixa(z.fc().fcMin(), z.fc().fcMax());
        return new ZonaDto(z.numero(), z.nome(), pace, fc, sprint ? MOTIVO_FC_Z6 : null);
    }

    private static int segundos(BigDecimal minutosDecimais) {
        return minutosDecimais.multiply(BigDecimal.valueOf(60)).setScale(0, java.math.RoundingMode.HALF_UP).intValue();
    }
}
