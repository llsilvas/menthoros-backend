package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.AthleteZoneAnchorsInputDto;
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
import br.com.menthoros.backend.services.helper.AncorasZonas;
import br.com.menthoros.backend.services.helper.AthleteZoneAnchorsValidator;
import br.com.menthoros.backend.services.helper.IntervalsIcuFcSyncer;
import br.com.menthoros.backend.services.helper.PaceZoneCalculator;
import br.com.menthoros.backend.services.helper.ZonaTreinoService;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaCompleta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * {@code buscarZonas}: leitura pura, tenant-aware. {@code salvarAncoras}: ver {@link CoachAthleteZonesService}.
 *
 * <p>Não recalcula zona: as faixas vêm de {@link ZonaTreinoService}, as mesmas que o prompt da IA
 * recebe. O ajuste por fadiga é só informado — as faixas ficam na base.
 */
@Slf4j
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
    private final AthleteZoneAnchorsValidator anchorsValidator;
    private final IntervalsIcuFcSyncer intervalsIcuFcSyncer;

    @Override
    @Transactional(readOnly = true)
    public AthleteTrainingZonesOutputDto buscarZonas(UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado"));

        return montar(AncorasZonas.de(atleta, tsbProntidao(atletaId, tenantId)));
    }

    /**
     * Deliberadamente SEM {@code @Transactional}: cada {@code UPDATE} do repositório commita sozinho, então
     * a releitura final enxerga o gravado e dois PUTs em eixos diferentes não se sobrescrevem (CA18).
     */
    @Override
    public AthleteTrainingZonesOutputDto salvarAncoras(UUID atletaId, AthleteZoneAnchorsInputDto input) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado"));
        anchorsValidator.validar(input, atleta);

        if (input.fc() != null) {
            var fc = input.fc();
            int linhas = fc.maxima() != null
                    ? atletaRepository.atualizarLimiarFcEMaxima(atletaId, tenantId, fc.limiar(), fc.maxima(),
                            fc.dataTeste(), fc.origem())
                    : atletaRepository.atualizarLimiarFc(atletaId, tenantId, fc.limiar(), fc.dataTeste(), fc.origem());
            exigirLinha(linhas);
            log.info("Âncora de FC gravada: atletaId={}, origem={}, anteriorEstimado={}",
                    atletaId, fc.origem(), atleta.getFcLimiar() == null);
            log.debug("Âncora de FC: atletaId={}, limiar={}, maxima={}", atletaId, fc.limiar(), fc.maxima());
            // Depois do commit do UPDATE (o método não é transacional) e antes de responder: falha só gera log.
            atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                    .ifPresent(salvo -> intervalsIcuFcSyncer.sincronizarSeConectado(salvo, tenantId));
        }
        if (input.pace() != null) {
            var pace = input.pace();
            exigirLinha(atletaRepository.atualizarLimiarPace(atletaId, tenantId,
                    AncorasZonas.paceEmMinutos(pace.limiarSegKm()), pace.dataTeste(), pace.origem()));
            log.info("Âncora de pace gravada: atletaId={}, origem={}, anteriorEstimado={}",
                    atletaId, pace.origem(), atleta.getPaceLimiar() == null);
            log.debug("Âncora de pace: atletaId={}, limiarSegKm={}", atletaId, pace.limiarSegKm());
        }
        return buscarZonas(atletaId);
    }

    @Override
    @Transactional(readOnly = true)
    public AthleteTrainingZonesOutputDto simular(UUID atletaId, AthleteZoneAnchorsInputDto input) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado"));
        anchorsValidator.validar(input, atleta);
        return montar(AncorasZonas.de(atleta, tsbProntidao(atletaId, tenantId)).com(input));
    }

    private Double tsbProntidao(UUID atletaId, UUID tenantId) {
        return planoMetadadosRepository.findLatestByAtletaIdAndTenantId(atletaId, tenantId)
                .map(PlanoMetaDados::getTsbProntidaoAtual).orElse(null);
    }

    private static void exigirLinha(int linhas) {
        if (linhas == 0) {
            throw new DomainNotFoundException("Atleta não encontrado");
        }
    }

    private AthleteTrainingZonesOutputDto montar(AncorasZonas a) {
        boolean temPace = a.temPace();
        int ajuste = temPace
                ? paceZoneCalculator.calcularZonasAjustadasPorTsbProntidao(a.paceLimiar(), a.tsbProntidao()).ajusteSegundos()
                : 0;

        List<ZonaDto> zonas = zonaTreinoService
                .calcularZonas(a.fcMaximaCalculada(), a.fcLimiarCalculada(), a.paceLimiar()).stream()
                .map(z -> toDto(z, temPace))
                .toList();

        return new AthleteTrainingZonesOutputDto(
                MODELO,
                new Ancoras(temPace ? a.paceLimiar() : null, a.dataTestePace(),
                        a.fcLimiarCalculada(), a.dataTesteFc(), a.fcLimiarEstimada(),
                        a.fontePace(), a.fonteFc()),
                new AjusteFadiga(ajuste, a.tsbProntidao()),
                new FcMaxima(a.fcMaximaComFallback(), a.fcMaximaEstimada()),
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
