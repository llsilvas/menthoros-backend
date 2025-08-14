package com.menthoros.services.impl;

import com.menthoros.dto.output.TreinoRealizadoOutputDto;
import com.menthoros.entity.PlanoMetaDados;
import com.menthoros.entity.PlanoSemanal;
import com.menthoros.entity.TreinoRealizado;
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
import com.menthoros.services.IaService;
import com.menthoros.services.PlanoService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class PlanoServiceImpl implements PlanoService {

    private final IaService iaService;
    private final AtletaRepository atletaRepository;
    private final AtletaMapper atletaMapper;
    private final TreinoMapper treinoMapper;
    private final PlanoMapper planoMapper;
    private final PlanoMetadadosRepository planoMetadadosRepository;
    private final PlanoSemanalMapper planoSemanalMapper;

    private final EmbeddingService embeddingService;
    private final PlanoSemanalRepository planoSemanalRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;

    @Transactional
    public PlanoSemanal gerarPlanoTreino(UUID atletaId) {
        var atleta = atletaRepository.findById(atletaId)
                .orElseThrow(() -> new RuntimeException("Atleta não encontrado"));

        // Verifica existência de plano atual
        var hoje = LocalDate.now();
        boolean planoAtualExiste = planoSemanalRepository
                .existsByAtletaIdAndSemanaInicioLessThanEqualAndSemanaFimGreaterThanEqualAndStatusNot(atletaId, hoje, hoje, PlanoStatus.CONCLUIDO);

        if (planoAtualExiste) {
            throw new IllegalStateException("Já existe um plano em andamento para esta semana.");
        }

        // Define nova data de início
        LocalDate dataInicio;
        var ultimoPlano = planoSemanalRepository
                .findTopByAtletaIdOrderBySemanaInicioDesc(atletaId);

        dataInicio = ultimoPlano.map(p -> p.getSemanaInicio().plusWeeks(1))
                .orElse(hoje.with(DayOfWeek.MONDAY));

        // Treinos realizados (últimos 7 dias ou todos)
        List<TreinoRealizado> realizados = treinoRealizadoRepository
                .findByAtletaIdOrderByDataTreinoDesc(atletaId);

        List<TreinoRealizadoOutputDto> ultimosTreinos = realizados.stream()
                .limit(7)
                .map(treinoMapper::toOutputDto)
                .toList();

        // MetaDados
        var meta = PlanoMetaDados.builder()
                .atleta(atleta)
                .diaPreferidoLongo(atleta.getDiaPreferidoLongo())
                .dataCriacao(LocalDateTime.now())
                .build();

        PlanoMetaDados metaDados = planoMetadadosRepository.save(meta);

        // IA
        var jsonPlano = iaService.gerarPlano(atletaMapper.toOutputDto(atleta), ultimosTreinos);
        PlanoSemanal plano = planoSemanalMapper.toEntity(jsonPlano);

        planoSemanalMapper.linkPlanoSemanal(plano);
        plano.setAtleta(atleta);
        plano.setPlanoMetaDados(metaDados);
        plano.setSemanaInicio(dataInicio);
        plano.setSemanaFim(dataInicio.plusDays(6));

        metaDados.setPlanoSemanal(plano);

        return planoSemanalRepository.save(plano);
    }

}

