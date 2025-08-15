package com.menthoros.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.menthoros.util.Utils;
import com.menthoros.dto.output.AtletaOutputDto;
import com.menthoros.dto.output.PlanoSemanalOutputDto;
import com.menthoros.dto.output.TreinoRealizadoOutputDto;
import com.menthoros.entity.*;
import com.menthoros.enums.DiaSemanaEnum;
import com.menthoros.enums.PlanoStatus;
import com.menthoros.mapper.AtletaMapper;
import com.menthoros.mapper.PlanoMapper;
import com.menthoros.mapper.PlanoSemanalMapper;
import com.menthoros.mapper.TreinoMapper;
import com.menthoros.repository.AtletaRepository;
import com.menthoros.repository.PlanoMetadadosRepository;
import com.menthoros.repository.PlanoSemanalRepository;
import com.menthoros.repository.TreinoRealizadoRepository;
import com.menthoros.services.EmbeddingService;
import com.menthoros.services.PlanoService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class PlanoServiceImpl implements PlanoService {

    private final IaServiceImpl iaService;
    private final AtletaRepository atletaRepository;
    private final AtletaMapper atletaMapper;
    private final TreinoMapper treinoMapper;
    private final PlanoMapper planoMapper;
    private final PlanoMetadadosRepository planoMetadadosRepository;
    private final PlanoSemanalMapper planoSemanalMapper;

    private final EmbeddingService embeddingService;
    private final PlanoSemanalRepository planoSemanalRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;

    @Override
    @Transactional
    public PlanoSemanal gerarPlanoTreino(UUID atletaId) throws JsonProcessingException {
        LocalDate hoje = LocalDate.now();

        Atleta atleta = atletaRepository.findById(atletaId)
                .orElseThrow(() -> new RuntimeException("Atleta não encontrado"));

    //        boolean planoAtualExiste = planoSemanalRepository
    //                .existsByAtletaIdAndSemanaInicioLessThanEqualAndSemanaFimGreaterThanEqualAndStatusNot(
    //                        atletaId, hoje, hoje, PlanoStatus.CONCLUIDO);
    //
    //        if (planoAtualExiste) {
    //            throw new IllegalStateException("Já existe um plano em andamento para esta semana.");
    //        }

        LocalDate dataInicio = planoSemanalRepository
                .findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)
                .map(p -> p.getSemanaInicio().plusWeeks(1))
                .orElse(hoje.with(TemporalAdjusters.next(DayOfWeek.MONDAY)));

        Optional<PlanoSemanal> planoAnteriorOpt = planoSemanalRepository.findTopByAtletaIdAndSemanaInicioBeforeAndStatusOrderBySemanaInicioDesc(atletaId, dataInicio, PlanoStatus.CONCLUIDO);

        PlanoSemanalOutputDto planoAnteriorDto = planoAnteriorOpt.map(planoSemanalMapper::toOutputDto).orElse(null);

        List<TreinoRealizado> realizados = treinoRealizadoRepository
                .findByAtletaIdOrderByDataTreinoDesc(atletaId);

        List<TreinoRealizadoOutputDto> ultimosTreinos = realizados.isEmpty()
                ? Collections.emptyList()
                : realizados.stream()
                    .limit(7)
                    .map(treinoMapper::toOutputDto)
                    .toList();

        Hibernate.initialize(atleta.getProvas()); // evita LazyInitializationException

        PlanoMetaDados meta = PlanoMetaDados.builder()
                .atleta(atleta)
                .diaPreferidoLongo(atleta.getDiaPreferidoLongo())
                .dataCriacao(LocalDateTime.now())
                .build();

        PlanoMetaDados planoMetaDados = planoMetadadosRepository.save(meta);

        // IA: bloqueante (fora do Reactor)
        PlanoSemanalOutputDto planoDto = iaService
                .gerarPlano(atletaMapper.toOutputDto(atleta), ultimosTreinos, planoAnteriorDto);


        PlanoSemanal plano = planoSemanalMapper.toEntity(planoDto);
        plano.setAtleta(atleta);
        plano.setPlanoMetaDados(planoMetaDados);
        plano.setSemanaInicio(dataInicio);
        plano.setSemanaFim(dataInicio.plusDays(6));

        List<TreinoPlanejado> treinosPlanejados = planoDto.treinosPlanejados().stream().map(dto -> {
            TreinoPlanejado treino = treinoMapper.toEntity(dto);
            treino.setPlanoSemanal(plano);
            treino.setDataTreino(calcularDataTreino(plano.getSemanaInicio(), dto.diaSemana()));
            return treino;
        }).toList();

        plano.setTreinosPlanejados(treinosPlanejados);

        planoMetaDados.setPlanoSemanal(plano);

        return planoSemanalRepository.save(plano);
    }

    private LocalDate calcularDataTreino(LocalDate semanaInicio, DiaSemanaEnum diaSemana) {
        DayOfWeek dayOfWeek = Utils.converterParaDayOfWeek(diaSemana);

        return semanaInicio.with(TemporalAdjusters.nextOrSame(dayOfWeek));
    }


    @Override
    public void deletePlanoSemanal(UUID planoSemanalId) {
        planoSemanalRepository.deleteById(planoSemanalId);
    }


    private PlanoSemanal getPlanoSemanal(PlanoSemanalOutputDto jsonPlano, DadosPlanoContext context) {
        PlanoSemanal plano = planoSemanalMapper.toEntity(jsonPlano);

        planoSemanalMapper.linkPlanoSemanal(plano);
        plano.setAtleta(context.atleta());
        plano.setPlanoMetaDados(context.metaDados());
        plano.setSemanaInicio(context.dataInicio());
        plano.setSemanaFim(context.dataInicio().plusDays(6));
        return plano;
    }

}

record DadosPlanoContext(Atleta atleta, AtletaOutputDto outputDto, PlanoMetaDados metaDados,
                         List<TreinoRealizadoOutputDto> ultimosTreinos, LocalDate dataInicio) {
}

