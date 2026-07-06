package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.TreinoManualInputDto;
import br.com.menthoros.backend.dto.input.TreinoRealizadoInputDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.dto.output.ResumoDetalhesDto;
import br.com.menthoros.backend.dto.output.ResumoSemanalTreinoDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.*;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.mapper.PlanoSemanalMapper;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.repository.*;
import br.com.menthoros.backend.events.TreinoRegistradoEvent;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.services.TreinoService;
import br.com.menthoros.backend.services.TsbService;
import br.com.menthoros.backend.services.helper.TipoTreinoConsistenciaValidator;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.IsoFields;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TreinoServiceImpl implements TreinoService {

    private final TreinoMapper treinoMapper;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final AtletaRepository atletaRepository;
    private final PlanoSemanalRepository planoSemanalRepository;
    private final PlanoSemanalMapper planoSemanalMapper;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final TsbService tsbService;
    private final PlanoMetadadosRepository planoMetaDadosRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TipoTreinoConsistenciaValidator tipoTreinoConsistenciaValidator;

    @Transactional
    @Override
    public TreinoRealizado addTreino(UUID treinoPlanejadoId, TreinoRealizadoInputDto treinoRealizado) {
        // 1) Duplicidade — usa externalId + atletaId para isolamento por tenant
        Optional<TreinoRealizado> duplicado = buscarTreinoDuplicado(treinoRealizado);
        if (duplicado.isPresent()) {
            log.warn("Treino já registrado: fonte={}, externalId={}", treinoRealizado.fonteDados(), treinoRealizado.externalId());
            return duplicado.get();
        }

        // 2) Carrega atleta — tenant-aware para prevenir cross-tenant data leak
        UUID tenantId = TenantContext.getRequiredTenantId();
        Atleta atleta = atletaRepository.findByIdAndTenantId(treinoRealizado.atletaId(), tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado"));

        // 3) Resolve planejado (id explícito OU conciliação automática)
        TreinoPlanejado planejado = resolveTreinoPlanejado(treinoPlanejadoId, treinoRealizado).orElse(null);

        // 4) Monta realizado
        TreinoRealizado realizado = montarTreinoRealizado(treinoRealizado, atleta, planejado);

        // 5) Resolve vínculo de plano semanal
        PlanoSemanal semanal = resolverPlanoSemanal(planejado, treinoRealizado, atleta).orElse(null);
        realizado.setPlanoSemanal(semanal);

        // 6) Persiste realizado
        TreinoRealizado salvo = treinoRealizadoRepository.save(realizado);

        // 7) Pós-processamentos isolados
        finalizarTreinoPlanejadoSeAplicavel(planejado);
        atualizarPlanoSemanalSeAplicavel(semanal);
        atualizarTsb(atleta, treinoRealizado.dataTreino());
        atualizarMetadadosSeAplicavel(semanal);
        atualizarVolumeDiario(atleta, treinoRealizado);

        return salvo;
    }

    private void atualizarVolumeDiario(Atleta atleta, TreinoRealizadoInputDto treinoRealizado) {
        LocalDate dataTreino = treinoRealizado.dataTreino() != null
            ? treinoRealizado.dataTreino()
            : LocalDate.now();

        var metricasDiarias = atleta.getMetricasDiarias();

        metricasDiarias.stream()
                .filter(md -> md.getData() != null && md.getData().equals(dataTreino))
                .findFirst()
                .ifPresent(md -> {
                    if (treinoRealizado.distanciaKm() != null) {
                        md.setVolumeKm(BigDecimal.valueOf(treinoRealizado.distanciaKm()));
                    }
                    md.setTreinosRealizados(md.getTreinosRealizados() + 1);
                });
    }

    private void atualizarMetadadosSeAplicavel(PlanoSemanal semanal) {
        if (semanal == null) return;
        atualizarMetadados(semanal.getId(), semanal.getAtleta().getId());
    }

    private void finalizarTreinoPlanejadoSeAplicavel(TreinoPlanejado planejado) {
        if (planejado == null) return;
        planejado.setStatusTreino(TreinoExecucaoStatus.REALIZADO);
        treinoPlanejadoRepository.save(planejado);
    }

    private void atualizarPlanoSemanalSeAplicavel(PlanoSemanal semanal) {
        if (semanal == null) return;
        double volume = treinoRealizadoRepository.sumDistanciaByPlanoSemanalId(semanal.getId());
        Hibernate.initialize(semanal);
        semanal.setVolumeRealizadoKm(BigDecimal.valueOf(volume));
        atualizarStatusDoPlano(semanal);
    }

    private void atualizarTsb(Atleta atleta, LocalDate dataTreino) {
        LocalDate dataParaAtualizar = dataTreino != null ? dataTreino : LocalDate.now();
        tsbService.atualizarTsbDia(atleta.getId(), dataParaAtualizar);
    }

    private void validarCompatibilidadeDeTipo(TreinoRealizadoInputDto input, TreinoPlanejado planejado) {
        if (planejado == null) return;
        if (!planejado.getTipoTreino().equals(input.tipoTreino())) {
            log.warn("Tipo do treino realizado ({}) difere do planejado ({}) para treinoPlanejadoId={}",
                    input.tipoTreino(), planejado.getTipoTreino(), planejado.getId());
            throw new DomainRuleViolationException("Tipo de treino incompatível com o planejado"); // evita salvar estado inválido
        }
    }

    private TreinoRealizado montarTreinoRealizado(TreinoRealizadoInputDto treinoRealizadoInputDto, Atleta atleta, TreinoPlanejado planejado) {
        // 4) Monta entidade realizado
        TreinoRealizado realizado = treinoMapper.toEntity(treinoRealizadoInputDto);
        realizado.setAtleta(atleta);
        realizado.setTenantId(atleta.getAssessoria().getId());
        realizado.setTreinoPlanejado(planejado);
        realizado.setStatus(TreinoExecucaoStatus.REALIZADO);
        realizado.setFonteDados(treinoRealizadoInputDto.fonteDados());
        realizado.setExternalId(treinoRealizadoInputDto.externalId());
        return realizado;
    }

    private Optional<PlanoSemanal> resolverPlanoSemanal(TreinoPlanejado planejado, TreinoRealizadoInputDto input, Atleta atleta) {
        if (planejado != null && planejado.getPlanoSemanal() != null) {
            return Optional.of(planejado.getPlanoSemanal());
        }
        if (input.dataTreino() == null) {
            return Optional.empty();
        }
        return planoSemanalRepository
                .findPlanoSemanalByAtletaIdAndTreinosPlanejadosDataTreino(atleta.getId(), input.dataTreino());
    }

    private Optional<TreinoPlanejado> resolveTreinoPlanejado(UUID treinoPlanejadoId, TreinoRealizadoInputDto treinoRealizadoInputDto) {
        if (treinoPlanejadoId != null) {
            // tenant-aware: garante que o treino planejado pertence ao tenant do contexto
            UUID tenantId = TenantContext.getRequiredTenantId();
            TreinoPlanejado planejado = treinoPlanejadoRepository.findByIdAndTenantId(treinoPlanejadoId, tenantId)
                    .orElseThrow(() -> new DomainNotFoundException("Treino planejado não encontrado"));
            if (!planejado.getAtleta().getId().equals(treinoRealizadoInputDto.atletaId())) {
                throw new DomainRuleViolationException("Treino planejado não pertence ao atleta.");
            }
//            validarCompatibilidadeDeTipo(input, p);
            return Optional.of(planejado);
        }
        // Conciliação automática
//        if (input.dataTreino() == null) {
//            return Optional.empty();
//        }
//        Optional<TreinoPlanejado> match = treinoPlanejadoRepository
//                .matchByAtletaAndDateAndType(atleta.getId(), input.dataTreino(), input.tipoTreino());
//        match.ifPresent(p -> validarCompatibilidadeDeTipo(input, p));
//        return match;
        return Optional.empty();
    }

    private void atualizarStatusDoPlano(PlanoSemanal plano) {
        List<TreinoPlanejado> treinos = plano.getTreinosPlanejados();

        long total = treinos.size();
        long realizados = treinos.stream()
                .filter(t -> t.getStatusTreino() == TreinoExecucaoStatus.REALIZADO)
                .count();
        long perdidos = treinos.stream()
                .filter(t -> t.getStatusTreino() == TreinoExecucaoStatus.PERDIDO)
                .count();
        long finalizados = realizados + perdidos;

        if (finalizados == 0) {
            plano.setStatus(PlanoStatus.PLANEJADO);
        } else if (finalizados == total) {
            plano.setStatus(PlanoStatus.CONCLUIDO);
        } else if (finalizados == 1) {
            plano.setStatus(PlanoStatus.INICIADO);
        } else {
            plano.setStatus(PlanoStatus.EM_ANDAMENTO);
        }
        planoSemanalRepository.save(plano);
    }

    private void atualizarMetadados(UUID planoSemanalId, UUID atletaId) {

        Optional<PlanoMetaDados> planoMetaDados = planoMetaDadosRepository.findByAtletaId(atletaId);
        double distancia = treinoRealizadoRepository.sumDistanciaByPlanoSemanalId(planoSemanalId);

        planoMetaDados.ifPresent(planoMetaDado -> {
            planoMetaDado.setVolumeSemanalMedio(BigDecimal.valueOf(distancia));
            planoMetaDadosRepository.save(planoMetaDado);
        });
    }


    /**
     * Busca treino duplicado usando externalId + atletaId para isolamento por tenant.
     * Cada atleta pertence a exatamente um tenant, portanto filtrar por atletaId
     * garante isolamento de dados entre tenants diferentes.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES — via atletaId (proxy de tenant)
     */
    private Optional<TreinoRealizado> buscarTreinoDuplicado(TreinoRealizadoInputDto treinoRealizadoInputDto) {
        if (treinoRealizadoInputDto.externalId() != null && treinoRealizadoInputDto.atletaId() != null) {
            return treinoRealizadoRepository
                    .findByExternalIdAndAtletaId(treinoRealizadoInputDto.externalId(), treinoRealizadoInputDto.atletaId());
        }
        return Optional.empty();
    }

    /**
     * Atualiza os campos observacionais de um treino realizado.
     *
     * Idempotent: NO — atualiza dados a cada chamada.
     * Side Effects: Database update + TreinoRegistradoEvent quando percepcaoEsforco != null.
     * Tenant-aware: YES — via TenantContext.getRequiredTenantId().
     */
    @Override
    @Transactional
    public TreinoRealizadoOutputDto updateTreino(UUID id, TreinoRealizadoInputDto dto) {
        UUID tenantId = TenantContext.getRequiredTenantId();

        TreinoRealizado treino = treinoRealizadoRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Treino não encontrado: " + id));

        applyMutableFields(treino, dto);

        TreinoRealizado salvo = treinoRealizadoRepository.save(treino);

        if (salvo.getPercepcaoEsforco() != null) {
            eventPublisher.publishEvent(new TreinoRegistradoEvent(salvo.getId(), salvo.getTenantId()));
        }

        // Validação estrutural informativa — nunca altera o tipo automaticamente
        tipoTreinoConsistenciaValidator.validarEstrutura(salvo).ifPresent(sug ->
            log.info("Sugestão de reclassificação para treino {}: {} → {} (confiança: {})",
                salvo.getId(), salvo.getTipoTreino(), sug.tipoSugerido(), sug.confianca())
        );

        return treinoMapper.toOutputDto(salvo);
    }

    private void applyMutableFields(TreinoRealizado treino, TreinoRealizadoInputDto dto) {
        if (dto.percepcaoEsforco() != null)           treino.setPercepcaoEsforco(dto.percepcaoEsforco());
        if (dto.feedbackAtleta() != null)             treino.setFeedbackAtleta(dto.feedbackAtleta());
        if (dto.qualidadeSonoNoiteAnterior() != null) treino.setQualidadeSonoNoiteAnterior(dto.qualidadeSonoNoiteAnterior());
        if (dto.nivelEstresse() != null)              treino.setNivelEstresse(dto.nivelEstresse());
        if (dto.fcMedia() != null)                    treino.setFcMedia(dto.fcMedia());
        if (dto.fcMax() != null)                      treino.setFcMax(dto.fcMax());
        if (dto.cadenciaMedia() != null)              treino.setCadenciaMedia(dto.cadenciaMedia());
        if (dto.potenciaMedia() != null)              treino.setPotenciaMedia(dto.potenciaMedia());
        if (dto.velocidadeMedia() != null)            treino.setVelocidadeMedia(dto.velocidadeMedia());
        if (dto.distanciaKm() != null)                treino.setDistanciaKm(new BigDecimal(dto.distanciaKm().toString()));
        if (dto.elevacaoGanhoMetros() != null)        treino.setElevacaoGanhoMetros(dto.elevacaoGanhoMetros());
        if (dto.elevacaoPerdaMetros() != null)        treino.setElevacaoPerdaMetros(dto.elevacaoPerdaMetros());
        if (dto.descricao() != null)                  treino.setDescricao(dto.descricao());
        if (dto.observacao() != null)                 treino.setObservacao(dto.observacao());
        if (dto.zonaAlvo() != null)                   treino.setZonaAlvo(dto.zonaAlvo());
        if (dto.ritmoAlvo() != null)                  treino.setRitmoAlvo(dto.ritmoAlvo());
        if (dto.status() != null)                     treino.setStatus(dto.status());
    }

    @Override
    public void deleteTreino(UUID id) {

    }

    @Override
    public TreinoRealizadoOutputDto getTreinoById(UUID id) {
        return null;
    }

    @Override
    public void gravarTreino(UUID atletaId, TreinoPlanejadoLlmDto planoSemanalOutputDto) {
        Atleta atleta = atletaRepository.findById(atletaId).orElseThrow();

        PlanoSemanal planoSemanal = planoSemanalMapper.toEntity(planoSemanalOutputDto);
        planoSemanal.setAtleta(atleta);

        planoSemanalRepository.save(planoSemanal);
    }

    @Override
    @Transactional
    public TreinoRealizadoOutputDto lancarTreino(UUID atletaId, TreinoRealizadoInputDto treinoRealizadoInputDto) {
        log.debug("Criando treino realizado: {}", treinoRealizadoInputDto);

        UUID tenantId = TenantContext.getRequiredTenantId();
        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado"));

        TreinoRealizado treinoRealizado = treinoMapper.toEntity(treinoRealizadoInputDto);

        treinoRealizado.setFonteDados(FonteDados.MANUAL);
        treinoRealizado.setStatus(TreinoExecucaoStatus.REALIZADO);
        treinoRealizado.setAtleta(atleta);
        treinoRealizado.setTenantId(atleta.getAssessoria().getId());

        TreinoRealizado treinoSalvo = treinoRealizadoRepository.save(treinoRealizado);
        log.info("Treino salvo com sucesso. ID: {}", treinoSalvo.getId());
        eventPublisher.publishEvent(new TreinoRegistradoEvent(treinoSalvo.getId(), treinoSalvo.getTenantId()));

        // Validação estrutural informativa — nunca altera o tipo automaticamente
        tipoTreinoConsistenciaValidator.validarEstrutura(treinoSalvo).ifPresent(sug ->
            log.info("Sugestão de reclassificação para treino {}: {} → {} (confiança: {})",
                treinoSalvo.getId(), treinoSalvo.getTipoTreino(), sug.tipoSugerido(), sug.confianca())
        );

        // Atualizar TSB/CTL/ATL após salvar o treino
        LocalDate dataTreino = treinoRealizadoInputDto.dataTreino() != null
            ? treinoRealizadoInputDto.dataTreino()
            : LocalDate.now();

        log.info("Atualizando métricas TSB para atleta {} na data {}", atletaId, dataTreino);
        tsbService.atualizarTsbDia(atletaId, dataTreino);

        return treinoMapper.toOutputDto(treinoSalvo);
    }

    private List<Float> gerarEmbedding(Atleta atleta) {
        return null;
    }

    private Integer calcularTSB(Atleta atleta) {
        return null;
    }

    private Double calcularVolumeUltimaSemana(Atleta atleta) {
        return null;
    }

    @Override
    @Transactional
    public void marcarTreinoPerdido(UUID treinoPlanejadoId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        TreinoPlanejado planejado = treinoPlanejadoRepository.findByIdAndTenantId(treinoPlanejadoId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Treino planejado não encontrado"));

        if (planejado.getStatusTreino() == TreinoExecucaoStatus.REALIZADO) {
            throw new DomainRuleViolationException("Treino já realizado não pode ser marcado como perdido");
        }
        if (planejado.getStatusTreino() == TreinoExecucaoStatus.PERDIDO) {
            log.warn("Treino {} já está marcado como perdido", treinoPlanejadoId);
            return;
        }

        planejado.setStatusTreino(TreinoExecucaoStatus.PERDIDO);
        treinoPlanejadoRepository.save(planejado);
        log.info("Treino {} marcado como perdido", treinoPlanejadoId);

        PlanoSemanal semanal = planejado.getPlanoSemanal();
        if (semanal != null) {
            Hibernate.initialize(semanal.getTreinosPlanejados());
            atualizarStatusDoPlano(semanal);
        }
    }

    @Transactional
    public void gravarTreino(List<PlanoSemanal> planoSemanalList) {
        planoSemanalRepository.saveAll(planoSemanalList);
    }

    @Override
    public ResumoSemanalTreinoDto getResumoSemanal(UUID atletaId, LocalDate targetDate) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado"));

        if (targetDate == null) {
            targetDate = LocalDate.now();
        }

        LocalDate startOfWeek = targetDate.with(WeekFields.of(Locale.ENGLISH).dayOfWeek(), 1);
        LocalDate endOfWeek = startOfWeek.plusDays(6);

        List<TreinoRealizado> treinos = treinoRealizadoRepository.findRealizedTrainingsByWeek(atletaId, startOfWeek, endOfWeek);

        Map<String, ResumoDetalhesDto> diasDaSemana = new HashMap<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            diasDaSemana.put(day.toString(), new ResumoDetalhesDto(0, 0.0, 0.0));
        }

        double totalKm = 0;
        double totalTss = 0;
        double totalMinutos = 0;
        LocalDate ultimoTreino = null;
        int diasComTreino = 0;

        Map<LocalDate, List<TreinoRealizado>> treinosPorDia = treinos.stream()
                .collect(Collectors.groupingBy(TreinoRealizado::getDataTreino));

        for (Map.Entry<LocalDate, List<TreinoRealizado>> entry : treinosPorDia.entrySet()) {
            diasComTreino++;
            double km = entry.getValue().stream()
                    .mapToDouble(t -> t.getDistanciaKm() != null ? t.getDistanciaKm().doubleValue() : 0)
                    .sum();
            double tss = entry.getValue().stream()
                    .mapToDouble(t -> t.getTssCalculado() != null ? t.getTssCalculado() : 0)
                    .sum();
            double minutos = entry.getValue().stream()
                    .mapToDouble(t -> t.getDuracaoMin() != null ? t.getDuracaoMin().toMinutes() : 0)
                    .sum();

            totalKm += km;
            totalTss += tss;
            totalMinutos += minutos;

            String dayName = entry.getKey().getDayOfWeek().toString();
            diasDaSemana.put(dayName, new ResumoDetalhesDto(entry.getValue().size(), km, tss));

            if (ultimoTreino == null || entry.getKey().isAfter(ultimoTreino)) {
                ultimoTreino = entry.getKey();
            }
        }

        int week = targetDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = targetDate.get(IsoFields.WEEK_BASED_YEAR);

        ResumoSemanalTreinoDto.Resumo resumo = new ResumoSemanalTreinoDto.Resumo(
                treinos.size(),
                totalKm,
                totalTss,
                totalMinutos,
                diasComTreino,
                7 - diasComTreino,
                ultimoTreino != null ? ultimoTreino.toString() : null,
                diasDaSemana
        );

        ResumoSemanalTreinoDto response = new ResumoSemanalTreinoDto(
                atletaId,
                atleta.getNome(),
                String.format("%04d-W%02d", year, week),
                startOfWeek.toString(),
                endOfWeek.toString(),
                resumo
        );

        return response;
    }

    private static final String CRIADO_POR_ATLETA = "ATLETA";

    /**
     * Registra treino executado manualmente pelo próprio atleta.
     *
     * <p><b>Idempotent:</b> NO — cria nova entidade a cada chamada.
     * <p><b>Side Effects:</b> Database insert (TreinoRealizado) + TreinoRegistradoEvent + TSB update.
     * <p><b>Tenant-aware:</b> YES — valida atleta via findByIdAndTenantId; query de match filtra por tenant.
     *
     * @param atletaId ID do atleta (resolvido do JWT no controller)
     * @param input    dados do treino manual
     * @return treino salvo como DTO
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException      se atletaId não pertence ao tenant
     * @throws br.com.menthoros.backend.exception.DomainRuleViolationException se data anterior a 7 dias
     */
    @Override
    @Transactional
    public TreinoRealizadoOutputDto registrarTreinoManualAtleta(UUID atletaId, TreinoManualInputDto input) {
        LocalDate hoje = LocalDate.now();
        if (input.data().isBefore(hoje.minusDays(7))) {
            throw new DomainRuleViolationException("Data do treino não pode ser anterior a 7 dias atrás.");
        }

        UUID tenantId = TenantContext.getRequiredTenantId();
        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado para o tenant"));

        // Best-effort match: busca TreinoPlanejado sem realizado vinculado para a mesma data/tipo
        List<TreinoExecucaoStatus> statusesElegiveis = List.of(
                TreinoExecucaoStatus.PENDENTE, TreinoExecucaoStatus.PERDIDO);
        Optional<TreinoPlanejado> matchOpt = treinoPlanejadoRepository
                .findFirstForManualMatch(atletaId, tenantId, input.data(), input.tipo(), statusesElegiveis);

        TreinoRealizado treino = new TreinoRealizado();
        treino.setAtleta(atleta);
        treino.setTenantId(tenantId);
        treino.setDataTreino(input.data());
        treino.setDiaSemana(toDiaSemana(input.data()));
        treino.setTipoTreino(input.tipo());
        treino.setDuracaoMin(Duration.ofMinutes(input.duracaoMinutos()));
        treino.setDistanciaKm(input.distanciaKm());
        treino.setPercepcaoEsforco(input.percepcaoEsforco());
        treino.setObservacao(input.observacoes());
        treino.setFonteDados(FonteDados.MANUAL);
        treino.setStatus(TreinoExecucaoStatus.REALIZADO);
        treino.setCriadoPor(CRIADO_POR_ATLETA);
        matchOpt.ifPresent(treino::setTreinoPlanejado);

        TreinoRealizado treinoSalvo = treinoRealizadoRepository.save(treino);
        log.info("Treino manual salvo: id={}, atletaId={}, data={}", treinoSalvo.getId(), atletaId, input.data());

        matchOpt.ifPresent(planejado -> {
            planejado.setStatusTreino(TreinoExecucaoStatus.REALIZADO);
            planejado.setTreinoRealizado(treinoSalvo);
            treinoPlanejadoRepository.save(planejado);
            // Reverter PERDIDO -> REALIZADO (registro retroativo) exige recalcular o plano.
            atualizarPlanoSemanalSeAplicavel(planejado.getPlanoSemanal());
            log.info("TreinoPlanejado {} vinculado ao treino manual {}", planejado.getId(), treinoSalvo.getId());
        });

        eventPublisher.publishEvent(new TreinoRegistradoEvent(treinoSalvo.getId(), tenantId));
        tsbService.atualizarTsbDia(atletaId, input.data());

        return treinoMapper.toOutputDto(treinoSalvo);
    }

    /**
     * Lista treinos realizados recentes do atleta, ordenados por data DESC.
     *
     * <p><b>Idempotent:</b> YES — leitura pura, sem alteração de estado.
     * <p><b>Side Effects:</b> NONE.
     * <p><b>Tenant-aware:</b> YES — filtra por tenantId na query.
     *
     * @param atletaId ID do atleta
     * @param dias     janela de consulta (limitado a 30 internamente)
     * @return lista de treinos realizados no período, ordenada por data DESC
     */
    @Override
    @Transactional(readOnly = true)
    public List<TreinoRealizadoOutputDto> listarTreinosRecentes(UUID atletaId, int dias) {
        int diasEfetivos = Math.min(dias, TreinoService.MAX_JANELA_DIAS);
        LocalDate hoje = LocalDate.now();
        UUID tenantId = TenantContext.getRequiredTenantId();
        return treinoRealizadoRepository
                .findByAtletaIdAndTenantIdAndDataTreinoBetween(atletaId, tenantId, hoje.minusDays(diasEfetivos), hoje)
                .stream()
                .map(treinoMapper::toOutputDto)
                .toList();
    }

    private DiaSemana toDiaSemana(LocalDate data) {
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
}
