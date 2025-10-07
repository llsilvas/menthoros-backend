package com.menthoros.services.impl;

import com.menthoros.dto.input.TreinoRealizadoInputDto;
import com.menthoros.dto.llm.TreinoPlanejadoLlmDto;
import com.menthoros.dto.output.TreinoRealizadoOutputDto;
import com.menthoros.entity.*;
import com.menthoros.enums.FonteDados;
import com.menthoros.enums.PlanoStatus;
import com.menthoros.enums.TreinoExecucaoStatus;
import com.menthoros.exception.DomainNotFoundException;
import com.menthoros.exception.DomainRuleViolationException;
import com.menthoros.mapper.PlanoSemanalMapper;
import com.menthoros.mapper.TreinoMapper;
import com.menthoros.repository.*;
import com.menthoros.services.TreinoService;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    private final TsbServiceImpl tsbService;
    private final PlanoMetadadosRepository planoMetaDadosRepository;

    @Transactional
    @Override
    public TreinoRealizado addTreino(UUID treinoPlanejadoId, TreinoRealizadoInputDto treinoRealizado) {
        // 1) Duplicidade
        Optional<TreinoRealizado> duplicado = buscarTreinoDuplicado(treinoRealizado);
        if (duplicado.isPresent()) {
            log.warn("Treino já registrado: fonte={}, externalId={}", treinoRealizado.fonteDados(), treinoRealizado.externalId());
            return duplicado.get(); //
        }

        // 2) Carrega atleta
        Atleta atleta = atletaRepository.findById(treinoRealizado.atletaId())
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado"));

        // 3) Resolve planejado (id explícito OU conciliação automática)
        TreinoPlanejado planejado = resolveTreinoPlanejado(treinoPlanejadoId, treinoRealizado).orElse(null);

        // 4) Monta realizado
        TreinoRealizado realizado = montarTreinoRealizado(treinoRealizado, atleta, planejado);

        // 5) Resolve vínculo de plano semanal
        PlanoSemanal semanal = resolverPlanoSemanal(planejado, treinoRealizado, atleta).orElse(null);
        if (semanal != null) {
            realizado.setPlanoSemanal(semanal);
        }

        // 6) Persiste realizado
        TreinoRealizado salvo = treinoRealizadoRepository.save(realizado);

        // 7) Pós-processamentos isolados
        finalizarTreinoPlanejadoSeAplicavel(planejado);
        atualizarPlanoSemanalSeAplicavel(semanal);
        atualizarTsb(atleta);
        atualizarMetadadosSeAplicavel(semanal);
        atualizarVolumeDiario(atleta, treinoRealizado);

        return salvo;
    }

    private void atualizarVolumeDiario(Atleta atleta, TreinoRealizadoInputDto treinoRealizado) {
        LocalDate hoje = LocalDate.now();
        var metricasDiarias = atleta.getMetricasDiarias();

        metricasDiarias.stream()
                .filter(md -> md.getData() == hoje)
                .findFirst()
                .ifPresent(md -> {
                    md.setVolumeKm(BigDecimal.valueOf(treinoRealizado.distanciaKm()));
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

    private void atualizarTsb(Atleta atleta) {

        tsbService.atualizarTsbDia(atleta.getId(), LocalDate.now());

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
            TreinoPlanejado planejado = treinoPlanejadoRepository.findById(treinoPlanejadoId)
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

    private void atualizarMetadados(UUID planoSemanalId, UUID atletaId) {

        Optional<PlanoMetaDados> planoMetaDados = planoMetaDadosRepository.findByAtletaId(atletaId);
        double distancia = treinoRealizadoRepository.sumDistanciaByPlanoSemanalId(planoSemanalId);

        planoMetaDados.ifPresent(planoMetaDado -> {
            planoMetaDado.setVolumeSemanalMedio(BigDecimal.valueOf(distancia));
            planoMetaDadosRepository.save(planoMetaDado);
        });
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

        Atleta atleta = atletaRepository.findById(atletaId)
                .orElseThrow(() -> new RuntimeException("Atleta não encontrado"));

        TreinoRealizado treinoRealizado = treinoMapper.toEntity(treinoRealizadoInputDto);

        treinoRealizado.setFonteDados(FonteDados.MANUAL);
        treinoRealizado.setStatus(TreinoExecucaoStatus.REALIZADO);
        treinoRealizado.setAtleta(atleta);

        TreinoRealizado treinoSalvo = treinoRealizadoRepository.save(treinoRealizado);
        log.info("Treino salvo com sucesso. ID: {}", treinoSalvo.getId());
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

    @Transactional
    public void gravarTreino(List<PlanoSemanal> planoSemanalList) {
        planoSemanalRepository.saveAll(planoSemanalList);
    }
}
