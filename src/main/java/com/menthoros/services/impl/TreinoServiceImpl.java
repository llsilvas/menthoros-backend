package com.menthoros.services.impl;

import com.menthoros.dto.input.TreinoRealizadoInputDto;
import com.menthoros.dto.output.PlanoSemanalOutputDto;
import com.menthoros.dto.output.TreinoRealizadoOutputDto;
import com.menthoros.entity.Atleta;
import com.menthoros.entity.PlanoSemanal;
import com.menthoros.entity.TreinoPlanejado;
import com.menthoros.entity.TreinoRealizado;
import com.menthoros.enums.PlanoStatus;
import com.menthoros.enums.StatusTreino;
import com.menthoros.mapper.PlanoSemanalMapper;
import com.menthoros.mapper.TreinoMapper;
import com.menthoros.repository.*;
import com.menthoros.services.TreinoService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class TreinoServiceImpl implements TreinoService {

    private final TreinoMapper treinoMapper;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final AtletaRepository atletaRepository;
    private final PlanoSemanalRepository planoSemanalRepository;
    private final PlanoSemanalMapper planoSemanalMapper;
    private final PlanoMetadadosRepository planoMetaDadosRepository;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final TsbServiceImpl tsbService;

    public TreinoServiceImpl(TreinoMapper treinoMapper, TreinoRealizadoRepository treinoRealizadoRepository, AtletaRepository atletaRepository, PlanoSemanalRepository planoSemanalRepository, PlanoSemanalMapper planoSemanalMapper, PlanoMetadadosRepository planoMetaDadosRepository, TreinoPlanejadoRepository treinoPlanejadoRepository, TsbServiceImpl tsbService) {
        this.treinoMapper = treinoMapper;
        this.treinoRealizadoRepository = treinoRealizadoRepository;
        this.atletaRepository = atletaRepository;
        this.planoSemanalRepository = planoSemanalRepository;
        this.planoSemanalMapper = planoSemanalMapper;
        this.planoMetaDadosRepository = planoMetaDadosRepository;
        this.treinoPlanejadoRepository = treinoPlanejadoRepository;
        this.tsbService = tsbService;
    }

    @Transactional
    @Override
    public TreinoRealizado addTreino(TreinoRealizadoInputDto treinoRealizadoInputDto) {

        // 1) Deduplicação por fonteDados + externalId
        Optional<TreinoRealizado> existente = buscarTreinoDuplicado(treinoRealizadoInputDto);

        if (existente.isPresent()) {
            log.warn("Treino já registrado: fonte={}, externalId={}", treinoRealizadoInputDto.fonteDados(), treinoRealizadoInputDto.externalId());
            return existente.get(); // ou lance uma exceção se preferir forçar unicidade
        }

        Atleta atleta = atletaRepository.findById(treinoRealizadoInputDto.atletaId())
                    .orElseThrow(() -> new IllegalArgumentException("Atleta não encontrado"));

        // 3) Planejado (se veio id)
        TreinoPlanejado planejado = null;
        if (treinoRealizadoInputDto.treinoPlanejadoId() != null) {
            planejado = treinoPlanejadoRepository.findById(treinoRealizadoInputDto.treinoPlanejadoId())
                    .orElseThrow(() -> new IllegalArgumentException("Treino planejado não encontrado"));
            if (!planejado.getAtleta().getId().equals(atleta.getId())) {
                throw new IllegalStateException("Treino planejado não pertence ao atleta.");
            }
        }

        // 4) Monta entidade realizado
        TreinoRealizado realizado = treinoMapper.toEntity(treinoRealizadoInputDto);
        realizado.setAtleta(atleta);
        realizado.setTreinoPlanejado(planejado);
        realizado.setStatus(StatusTreino.REALIZADO);
        realizado.setFonteDados(treinoRealizadoInputDto.fonteDados());
        realizado.setExternalId(treinoRealizadoInputDto.externalId());
        realizado.setElevacaoTotalMetros(treinoRealizadoInputDto.elevacaoTotalMetros());

        // 5) Conciliação automática se não veio planejado
        if (planejado == null && treinoRealizadoInputDto.dataTreino() != null) {
            planejado = treinoPlanejadoRepository
                    .matchByAtletaAndDateAndType(
                            atleta.getId(),
                            treinoRealizadoInputDto.dataTreino(),
                            treinoRealizadoInputDto.tipoTreino()
                    )
                    .orElse(null);
            if (planejado != null) {
                realizado.setTreinoPlanejado(planejado);
            }

            if (planejado != null && !planejado.getTipoTreino().equals(treinoRealizadoInputDto.tipoTreino())) {
                log.warn("Tipo do treino realizado ({}) difere do planejado ({}) para treinoPlanejadoId={}",
                        treinoRealizadoInputDto.tipoTreino(), planejado.getTipoTreino(), planejado.getId());

                // Você pode lançar exceção ou apenas registrar e seguir
                 throw new IllegalStateException("Tipo de treino incompatível com o planejado");
            }
        }

        // 6) Vincula PlanoSemanal
        PlanoSemanal semanal = null;
        if (planejado != null && planejado.getPlanoSemanal() != null) {
            semanal = planejado.getPlanoSemanal();
        } else if (treinoRealizadoInputDto.dataTreino() != null) {
            semanal = planoSemanalRepository
                    .findPlanoSemanalByAtletaIdAndTreinosPlanejadosDataTreino(atleta.getId(), treinoRealizadoInputDto.dataTreino())
                    .orElse(null);
        }
        if (semanal != null) {
            realizado.setPlanoSemanal(semanal);
        }

        // 7) Persiste realizado
        TreinoRealizado salvo = treinoRealizadoRepository.save(realizado);

        // 8) Marca planejado como concluído (se houver)
        if (planejado != null) {
            planejado.setStatusTreino(StatusTreino.REALIZADO); // ou CONCLUIDO, conforme seu enum
            treinoPlanejadoRepository.save(planejado);
        }

        // 9) Atualiza volume da semana (somatório dos realizados)
        if (semanal != null) {
            double volume = treinoRealizadoRepository.sumDistanciaByPlanoSemanalId(semanal.getId());
            Hibernate.initialize(semanal);
            semanal.setVolumeRealizadoKm(volume);
            atualizarStatusDoPlano(semanal);
            planoSemanalRepository.save(semanal);
        }

        tsbService.atualizarTsb(atleta.getId(), semanal.getId());

        return salvo;
    }

    private void atualizarStatusDoPlano(PlanoSemanal plano) {
        List<TreinoPlanejado> treinos = plano.getTreinosPlanejados();

        long total = treinos.size();
        long realizados = treinos.stream()
                .filter(t -> t.getStatusTreino() == StatusTreino.REALIZADO)
                .count();

        if (realizados == 0) {
            plano.setStatus(PlanoStatus.PLANEJADO);
        } else if (realizados == total) {
            plano.setStatus(PlanoStatus.CONCLUIDO);
        } else if (realizados == 1) {
            plano.setStatus(PlanoStatus.INICIADO);
        } else {
            plano.setStatus(PlanoStatus.EM_ANDAMENTO);
        }

        planoSemanalRepository.save(plano);
    }


    private Optional<TreinoRealizado> buscarTreinoDuplicado(TreinoRealizadoInputDto treinoRealizadoInputDto) {
        if (treinoRealizadoInputDto.fonteDados() != null && treinoRealizadoInputDto.externalId() != null) {
            return treinoRealizadoRepository
                    .findByFonteDadosAndExternalId(treinoRealizadoInputDto.fonteDados(), treinoRealizadoInputDto.externalId());
        }
        return Optional.empty();
    }

    @Override
    public TreinoRealizado updateTreino(UUID id, TreinoRealizadoInputDto treinoRealizadoInputDto) {
        return null;
    }

    @Override
    public void deleteTreino(UUID id) {

    }

    @Override
    public TreinoRealizadoOutputDto getTreinoById(UUID id) {
        return null;
    }

    @Override
    public void gravarTreino(PlanoSemanalOutputDto planoSemanalOutputDto) {
        Atleta atleta = atletaRepository.findById(planoSemanalOutputDto.atletaId()).orElseThrow();

        PlanoSemanal planoSemanal = planoSemanalMapper.toEntity(planoSemanalOutputDto);

        planoSemanalRepository.save(planoSemanal);
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

    @Transactional
    public void gravarTreino(List<PlanoSemanal> planoSemanalList) {
        planoSemanalRepository.saveAll(planoSemanalList);
    }
}
