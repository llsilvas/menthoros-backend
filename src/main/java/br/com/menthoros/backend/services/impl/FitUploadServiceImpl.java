package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.fit.FitImportResultado;
import br.com.menthoros.backend.dto.fit.FitLapData;
import br.com.menthoros.backend.dto.fit.FitSessionData;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.events.TreinoRegistradoEvent;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.FitParseException;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.FitParseService;
import br.com.menthoros.backend.services.FitUploadService;
import br.com.menthoros.backend.services.TsbService;
import br.com.menthoros.backend.services.helper.TssCalculatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FitUploadServiceImpl implements FitUploadService {

    private static final String CRIADO_POR_ATLETA = "ATLETA";

    private final FitParseService fitParseService;
    private final AtletaRepository atletaRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final TreinoMapper treinoMapper;
    private final TsbService tsbService;
    private final TssCalculatorService tssCalculatorService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public FitImportResultado importar(UUID atletaId, InputStream in) {
        FitSessionData dados = fitParseService.parse(in);

        UUID tenantId = TenantContext.getRequiredTenantId();
        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado no tenant atual"));

        String externalId = buildExternalId(atletaId, dados);

        Optional<TreinoRealizado> existente = treinoRealizadoRepository
                .findByExternalIdAndAtletaId(externalId, atletaId);
        if (existente.isPresent()) {
            log.info("Fit já importado anteriormente: externalId={}, atletaId={}", externalId, atletaId);
            return new FitImportResultado(treinoMapper.toOutputDto(existente.get()), false);
        }

        TreinoRealizado treino = montarTreino(dados, atleta, tenantId, externalId);
        TreinoRealizado salvo = saveIdempotent(treino, externalId, atletaId);

        eventPublisher.publishEvent(new TreinoRegistradoEvent(salvo.getId(), tenantId));
        tsbService.atualizarTsbDia(atletaId, salvo.getDataTreino());
        log.info("Fit importado: treinoId={}, atletaId={}, externalId={}", salvo.getId(), atletaId, externalId);

        return new FitImportResultado(treinoMapper.toOutputDto(salvo), true);
    }

    /**
     * Inclui o atletaId no externalId — a constraint real de dedup é
     * UNIQUE(tenant_id, fonte_dados, external_id), escopada por TENANT, não por atleta (D0.2).
     * Sem isso, dois atletas do mesmo tenant com dispositivos que produzam o mesmo
     * serialNumber+startTime colidiriam na constraint.
     */
    private String buildExternalId(UUID atletaId, FitSessionData dados) {
        long serial = dados.serialNumber() != null ? dados.serialNumber() : 0L;
        return atletaId + "-" + serial + "-" + dados.startTimeEpochSeconds();
    }

    private TreinoRealizado montarTreino(FitSessionData dados, Atleta atleta, UUID tenantId, String externalId) {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setAtleta(atleta);
        treino.setTenantId(tenantId);
        treino.setDataTreino(dados.dataTreino());
        treino.setDiaSemana(toDiaSemana(dados.dataTreino()));
        // TipoTreino não discrimina esporte (só propósito de treino de corrida) — nunca fabricar
        // INTERVALADO/TIRO/etc. a partir da estrutura do .fit (D0.6).
        treino.setTipoTreino(TipoTreino.CONTINUO);
        treino.setDescricao(descricaoParaEsporte(dados));
        treino.setDuracaoMin(dados.duracao());
        treino.setDistanciaKm(toBigDecimal(dados.distanciaKm()));
        treino.setFcMedia(dados.fcMedia());
        treino.setFcMax(dados.fcMax());
        treino.setFonteDados(FonteDados.MANUAL);
        treino.setStatus(TreinoExecucaoStatus.REALIZADO);
        treino.setCriadoPor(CRIADO_POR_ATLETA);
        treino.setExternalId(externalId);

        if (dados.tssCalculado() != null) {
            treino.setTssCalculado(dados.tssCalculado());
            treino.setMetodoCalculoTss("DISPOSITIVO");
        } else {
            // TSS ausente no .fit (dispositivo mais antigo ou não-Garmin) — calcula via FC/duração
            // já preenchidos no treino (D0.3). Pode divergir do TSS do próprio dispositivo — esperado.
            treino.setTssCalculado(tssCalculatorService.calcularTss(treino));
            treino.setMetodoCalculoTss("FC");
        }

        for (FitLapData lap : dados.laps()) {
            EtapaRealizada etapa = EtapaRealizada.builder()
                    .treinoRealizado(treino)
                    .ordem(lap.ordem())
                    .splitIndex(lap.ordem())
                    .descricao("Lap " + lap.ordem())
                    .duracao(lap.duracao())
                    .distanciaKm(toBigDecimal(lap.distanciaKm()))
                    .fcMedia(lap.fcMedia())
                    .fcMax(lap.fcMax())
                    .build();
            treino.getEtapasRealizadas().add(etapa);
        }

        return treino;
    }

    private String descricaoParaEsporte(FitSessionData dados) {
        if (dados.corrida()) {
            return "Importado de .fit";
        }
        return "Importado de .fit — esporte: " + dados.esporteDetectado();
    }

    private BigDecimal toBigDecimal(Double valor) {
        return valor != null ? BigDecimal.valueOf(valor).setScale(3, RoundingMode.HALF_UP) : null;
    }

    private DiaSemana toDiaSemana(java.time.LocalDate data) {
        return switch (data.getDayOfWeek()) {
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
     * Idempotente sob concorrência: se dois uploads do mesmo .fit colidirem na constraint única
     * entre o pré-check e o insert, retorna o registro que "venceu" a corrida em vez de falhar
     * (mesmo padrão de StravaActivityServiceImpl.saveIdempotent — não reusado diretamente pois é
     * privado naquela classe, então replicado aqui).
     */
    private TreinoRealizado saveIdempotent(TreinoRealizado treino, String externalId, UUID atletaId) {
        try {
            return treinoRealizadoRepository.save(treino);
        } catch (DataIntegrityViolationException e) {
            log.warn("Corrida de concorrência no import de .fit (externalId={}, atletaId={}), buscando registro existente.",
                    externalId, atletaId);
            return treinoRealizadoRepository.findByExternalIdAndAtletaId(externalId, atletaId)
                    .orElseThrow(() -> e);
        }
    }
}
