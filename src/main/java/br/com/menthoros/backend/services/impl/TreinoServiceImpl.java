package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.TreinoRealizadoInputDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.*;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.mapper.PlanoSemanalMapper;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.repository.*;
import br.com.menthoros.backend.services.TreinoService;
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
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado"));

        TreinoRealizado treinoRealizado = treinoMapper.toEntity(treinoRealizadoInputDto);

        treinoRealizado.setFonteDados(FonteDados.MANUAL);
        treinoRealizado.setStatus(TreinoExecucaoStatus.REALIZADO);
        treinoRealizado.setAtleta(atleta);
        treinoRealizado.setTenantId(atleta.getAssessoria().getId());

        TreinoRealizado treinoSalvo = treinoRealizadoRepository.save(treinoRealizado);
        log.info("Treino salvo com sucesso. ID: {}", treinoSalvo.getId());

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
        TreinoPlanejado planejado = treinoPlanejadoRepository.findById(treinoPlanejadoId)
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
}
