package com.menthoros.services.impl;

import com.menthoros.dto.input.DadosPlanoDto;
import com.menthoros.dto.llm.PlanoSemanalLlmDto;
import com.menthoros.dto.output.PlanoSemanalOutputDto;
import com.menthoros.dto.output.TreinoRealizadoOutputDto;
import com.menthoros.entity.*;
import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.PlanoStatus;
import com.menthoros.exception.LLMException;
import com.menthoros.exception.ResourceNotFoundException;
import com.menthoros.mapper.AtletaMapper;
import com.menthoros.mapper.PlanoSemanalMapper;
import com.menthoros.mapper.TreinoMapper;
import com.menthoros.repository.AtletaRepository;
import com.menthoros.repository.PlanoMetadadosRepository;
import com.menthoros.repository.PlanoSemanalRepository;
import com.menthoros.repository.TreinoRealizadoRepository;
import com.menthoros.services.EmbeddingService;
import com.menthoros.services.PlanoService;
import com.menthoros.util.Utils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class PlanoServiceImpl implements PlanoService {

    private final IaServiceImpl iaService;
    private final AtletaRepository atletaRepository;
    private final AtletaMapper atletaMapper;
    private final TreinoMapper treinoMapper;
    private final PlanoSemanalMapper planoSemanalMapper;
    private final PlanoMetadadosRepository planoMetadadosRepository;

    private final EmbeddingService embeddingService;
    private final PlanoSemanalRepository planoSemanalRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;

    @Override
    @Transactional
    public PlanoSemanal gerarPlanoTreino(UUID atletaId) {

        DadosPlanoDto dadosPlano = getPreparaDadosPlano(atletaId);

        Hibernate.initialize(dadosPlano.atleta().getProvas()); // evita LazyInitializationException

        PlanoSemanalLlmDto planoDto = gerarPlanoSemanal(dadosPlano);

        return persistirPlanoCompleto(planoDto, dadosPlano);
    }

    private PlanoSemanal persistirPlanoCompleto(PlanoSemanalLlmDto planoDto, DadosPlanoDto dadosPlano) {

        Atleta atleta = atletaRepository.findById(dadosPlano.atleta().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado: " + dadosPlano.atleta().getId()));

        PlanoMetaDados metaDados;

        if(dadosPlano.metaDados().getId() != null){
            metaDados = planoMetadadosRepository.save(dadosPlano.metaDados());
        }else {
            metaDados = dadosPlano.metaDados();
        }

        PlanoSemanal plano = planoSemanalMapper.toEntity(planoDto);
        plano.setAtleta(atleta);
        plano.setPlanoMetaDados(metaDados);
        plano.setSemanaInicio(dadosPlano.dataInicio());
        plano.setSemanaFim(dadosPlano.dataInicio().plusDays(6));

        List<TreinoPlanejado> treinosPlanejados = planoDto.treinosPlanejados().stream().map(dto -> {
            TreinoPlanejado treino = treinoMapper.toEntity(dto);
            treino.setPlanoSemanal(plano);
            treino.setDataTreino(calcularDataTreino(plano.getSemanaInicio(), dto.diaSemana()));
            return treino;
        }).toList();

        plano.setTreinosPlanejados(treinosPlanejados);

        metaDados.setPlanoSemanal(plano);

        return planoSemanalRepository.save(plano);
    }

    private DadosPlanoDto getPreparaDadosPlano(UUID atletaId) {
        Atleta atleta = atletaRepository.findById(atletaId)
                .orElseThrow(() -> new RuntimeException("Atleta não encontrado"));

        //        boolean planoAtualExiste = planoSemanalRepository
        //                .existsByAtletaIdAndSemanaInicioLessThanEqualAndSemanaFimGreaterThanEqualAndStatusNot(
        //                        atletaId, hoje, hoje, PlanoStatus.CONCLUIDO);
        //
        //        if (planoAtualExiste) {
        //            throw new IllegalStateException("Já existe um plano em andamento para esta semana.");
        //        }

        PlanoMetaDados metaDados = buscarPlanoMetadados(atleta);

        List<TreinoRealizado> realizados = treinoRealizadoRepository
                .findByAtletaIdOrderByDataTreinoDesc(atletaId);

        List<TreinoRealizadoOutputDto> ultimosTreinos = realizados.isEmpty()
                ? Collections.emptyList()
                : realizados.stream()
                .limit(7)
                .map(treinoMapper::toOutputDto)
                .toList();

        //TODO: atualizar regra de data inicio

        LocalDate dataInicio = planoSemanalRepository
                .findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)
                .map(p -> p.getSemanaInicio().plusWeeks(1))
                .orElse(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));

        PlanoSemanalOutputDto planoAnterior = planoSemanalRepository.findTopByAtletaIdAndSemanaInicioBeforeAndStatusOrderBySemanaInicioDesc(atletaId, dataInicio, PlanoStatus.CONCLUIDO).map(planoSemanalMapper::toOutputDto).orElse(null);

        return new DadosPlanoDto(atleta, dataInicio, planoAnterior, ultimosTreinos, metaDados);
    }

    @Cacheable(value = "metadados-atleta", key = "#atletaId")
    public PlanoMetaDados buscarPlanoMetadados(Atleta atleta) {
        return planoMetadadosRepository.findLatestByAtletaId(atleta.getId())
                .orElseGet(() -> {
                    log.info("Criando novos metadados para o atleta: {} " + atleta.getId());
                    PlanoMetaDados novoMetaDados = PlanoMetaDados.builder()
                            .atleta(atleta)
                            .diaPreferidoLongo(atleta.getDiaPreferidoLongo())
                            .dataCriacao(LocalDateTime.now())
                            .build();

                    return planoMetadadosRepository.save(novoMetaDados);
                });
    }

    private PlanoSemanalLlmDto gerarPlanoSemanal(DadosPlanoDto dadosPlanoDto) {
        try {
            log.info("Iniciando geração de plano para atleta: {}", dadosPlanoDto.atleta().getId());

            PlanoSemanalLlmDto planoDto = iaService.gerarPlanoSemanal(
                    atletaMapper.toOutputDto(dadosPlanoDto.atleta()),
                    dadosPlanoDto.ultimosTreinos(),
                    dadosPlanoDto.planoAnterior());

            validaPlanoGerado(planoDto);

            log.info("Plano gerado com sucesso para o atleta: {}", dadosPlanoDto.atleta().getId());
            return planoDto;
        } catch (LLMException e) {
            log.error("Falha na IA ao gerar o plano para o atleta: {}", dadosPlanoDto.atleta().getId());
//        }catch (JsonProcessingException e){
//            log.error("Erro de JSON ao processar o plano para o atleta: {}", atleta.getId(), e);
        } catch (Exception e) {
            log.error("Erro inesperado ao gerar o plano para o atleta: {}", dadosPlanoDto.atleta().getId(), e);
        }
        return null;
    }

    private void validaPlanoGerado(PlanoSemanalLlmDto planoDto) {

        if (planoDto == null) {
            throw new IllegalStateException("IA retornou plano nulo");
        }

        if (planoDto.treinosPlanejados() == null || planoDto.treinosPlanejados().isEmpty()) {
            throw new IllegalStateException("IA retornou plano sem treinos");
        }

    }

    private LocalDate calcularDataTreino(LocalDate semanaInicio, DiaSemana diaSemana) {
        DayOfWeek dayOfWeek = Utils.converterParaDayOfWeek(diaSemana);

        return semanaInicio.with(TemporalAdjusters.nextOrSame(dayOfWeek));
    }


    @Override
    public void deletePlanoSemanal(UUID planoSemanalId) {
        planoSemanalRepository.deleteById(planoSemanalId);
    }

    @Transactional
    public PlanoSemanalOutputDto buscarPlanoPorAtleta(UUID atletaId) {
        PlanoSemanal planoSemanal = planoSemanalRepository.findByAtletaId(atletaId).orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado: " + atletaId));
        Hibernate.initialize(planoSemanal.getTreinosPlanejados());
        return planoSemanalMapper.toOutputDto(planoSemanal);
    }
}



