package com.menthoros.services.impl;

import com.menthoros.dto.input.DadosPlanoDto;
import com.menthoros.dto.llm.PlanoSemanalLlmDto;
import com.menthoros.dto.llm.TreinoPlanejadoLlmDto;
import com.menthoros.dto.output.PlanoSemanalOutputDto;
import com.menthoros.dto.output.TreinoRealizadoOutputDto;
import com.menthoros.entity.*;
import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.ModoGeracaoPlano;
import com.menthoros.enums.PlanoStatus;
import com.menthoros.exception.DomainRuleViolationException;
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
import com.menthoros.services.IaService;
import com.menthoros.services.PlanoService;
import com.menthoros.services.helper.RedistribuicaoTreinoHelper;
import com.menthoros.services.helper.RegraGeracaoTreino;
import com.menthoros.util.Utils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class PlanoServiceImpl implements PlanoService {

    private final IaService iaService;
    private final AtletaRepository atletaRepository;
    private final AtletaMapper atletaMapper;
    private final TreinoMapper treinoMapper;
    private final PlanoSemanalMapper planoSemanalMapper;
    private final PlanoMetadadosRepository planoMetadadosRepository;

    private final EmbeddingService embeddingService;
    private final PlanoSemanalRepository planoSemanalRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final RedistribuicaoTreinoHelper redistribuicaoHelper;
    private final RegraGeracaoTreino regraGeracaoTreino;

    @Transactional
    @Override
    public PlanoSemanal gerarPlanoTreino(UUID atletaId, ModoGeracaoPlano modoGeracao) {

        DadosPlanoDto dadosPlano = getPreparaDadosPlano(atletaId);
        Hibernate.initialize(dadosPlano.atleta().getProvas()); // evita LazyInitializationException

        try {
            PlanoSemanalLlmDto planoDto = gerarPlanoSemanal(dadosPlano);

            // Fail-fast: valida se o plano foi gerado antes de prosseguir
            if (planoDto == null) {
                throw new LLMException("Falha ao gerar plano: IA retornou resposta nula. Tente novamente.");
            }

            return persistirPlanoCompleto(planoDto, dadosPlano, modoGeracao);
        } catch (LLMException | DomainRuleViolationException e) {
            // Re-lança exceções de domínio sem modificar
            log.error("Erro de domínio ao gerar plano para atleta {}: {}", atletaId, e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            // Converte exceções de validação em exceções de domínio
            log.error("Erro de validação ao gerar plano para atleta {}: {}", atletaId, e.getMessage());
            throw new LLMException("Erro ao gerar plano semanal: " + e.getMessage(), e);
        } catch (Exception e) {
            // Captura exceções inesperadas
            log.error("Erro inesperado ao gerar plano para atleta {}", atletaId, e);
            throw new LLMException("Erro inesperado ao gerar plano. Por favor, tente novamente.", e);
        }
    }

    private PlanoSemanal persistirPlanoCompleto(PlanoSemanalLlmDto planoDto, DadosPlanoDto dadosPlano, ModoGeracaoPlano modoGeracao) {

        List<TreinoPlanejadoLlmDto> treinos;
        LocalDate hoje = LocalDate.now();
        Atleta atleta = dadosPlano.atleta();

        // 1. Calcular datas do plano
        LocalDate semanaInicio = calcularSemanaInicio(atleta.getId(), hoje, modoGeracao);
        LocalDate semanaFim = semanaInicio.plusDays(6);

        log.info("Plano: {} a {} (Modo: {})", semanaInicio, semanaFim, modoGeracao);

        // 2. REDISTRIBUIR TREINOS DA LLM
        if (ModoGeracaoPlano.SEMANA_ATUAL.equals(modoGeracao)) {
            treinos = redistribuicaoHelper.redistribuirTreinos(
                    planoDto.treinosPlanejados(),
                    atleta.getDiasDisponiveis(),
                    hoje,
                    semanaInicio,
                    semanaFim,
                    modoGeracao
            );
        } else {
            treinos = planoDto.treinosPlanejados();
        }

        // 3. Validar se há treinos após redistribuição
        if (treinos.isEmpty()) {
            throw new DomainRuleViolationException(
                    """
                    Não foi possível gerar treinos para a semana selecionada.
                        Motivos possíveis:
                        - Geração no meio da semana sem dias disponíveis
                        - Todos os treinos da LLM são incompatíveis (LONGO/INTERVALADO)
                        Sugestão: Gere para a próxima semana.
                    """
            );
        }

        PlanoMetaDados metaDados = prepararMetadados(planoDto, dadosPlano);

        PlanoSemanal plano = criarPlanoEntity(planoDto, atleta, semanaInicio, semanaFim, metaDados);

        // 6. Converter DTOs redistribuídos em entidades
        List<TreinoPlanejado> treinosPlanejados = treinos.stream()
                .map(dto -> {
                    TreinoPlanejado treino = treinoMapper.toEntity(dto);
                    treino.setPlanoSemanal(plano);

                    // Calcular data do treino
                    DiaSemana diaSemana = DiaSemana.valueOf(dto.diaSemana());
                    LocalDate dataTreino = calcularDataTreino(semanaInicio, diaSemana);
                    treino.setDataTreino(dataTreino);

                    return treino;
                })
                .toList();

        var volumePlanejado = treinosPlanejados.stream()
                .mapToDouble(this::distanciaTreinoPlanejado)
                .sum();

        plano.setVolumePlanejadoKm(BigDecimal.valueOf(volumePlanejado));
        plano.setVolumeAlvoKm(BigDecimal.valueOf(volumePlanejado));
        plano.setTreinosPlanejados(treinosPlanejados);

        PlanoSemanal planoSalvo = planoSemanalRepository.save(plano);

        metaDados.setPlanoSemanalAtual(planoSalvo);
        planoMetadadosRepository.save(metaDados);

        log.info("✅ Plano salvo - {} treinos redistribuídos, volume: {}km",
                treinosPlanejados.size(), volumePlanejado);

        return planoSalvo;
    }

    private LocalDate calcularSemanaInicio(UUID atletaId, LocalDate hoje, ModoGeracaoPlano modoGeracao) {
        return planoSemanalRepository
                .findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)
                .map(p -> p.getSemanaInicio().plusWeeks(1))
                .orElse(hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
    }

    private PlanoSemanal criarPlanoEntity(PlanoSemanalLlmDto planoDto, Atleta atleta, LocalDate semanaInicio, LocalDate semanaFim, PlanoMetaDados metaDados) {
        // Busca o último plano para pegar a média histórica
        PlanoSemanal ultimoPlano = planoSemanalRepository
                .findTopByAtletaIdOrderBySemanaInicioDesc(atleta.getId())
                .orElse(null);

        PlanoSemanal plano = planoSemanalMapper.toEntity(planoDto);
        plano.setAtleta(atleta);

        plano.setSemanaInicio(semanaInicio);
        plano.setSemanaFim(semanaFim);

        if (ultimoPlano != null && ultimoPlano.getPlanoMetaDados() != null) {
            var mediaSemanalHistorica = ultimoPlano.getPlanoMetaDados().getVolumeSemanalMedio();

            var volumePlanejado = mediaSemanalHistorica.multiply(BigDecimal.valueOf(1.10));

            metaDados.setVolumePlanejado(volumePlanejado);
            metaDados.setVolumeSemanalMedio(mediaSemanalHistorica);

        }

        plano.setPlanoMetaDados(metaDados);
        return plano;
    }

    private PlanoMetaDados prepararMetadados(PlanoSemanalLlmDto planoDto, DadosPlanoDto dadosPlano) {
        PlanoMetaDados metaDados;
        if (dadosPlano.metaDados().getId() != null) {
            metaDados = dadosPlano.metaDados();

            // Atualizar TSB projetado
            if (planoDto.tsbFim() != null) {
                metaDados.setTsbAtual(planoDto.tsbFim());
            }

            // Calcular e atualizar Ramp Rate
            atualizarRampRate(metaDados, planoDto.volumePlanejadoKm());

            // Atualizar contadores de progressão
            atualizarProgressao(metaDados, planoDto.volumePlanejadoKm());

            metaDados = planoMetadadosRepository.save(metaDados);
        } else {
            metaDados = dadosPlano.metaDados();
        }
        return metaDados;
    }

    private double distanciaTreinoPlanejado(TreinoPlanejado treinoPlanejado) {
        if (treinoPlanejado.getDistanciaKm() != null) return treinoPlanejado.getDistanciaKm().doubleValue();

        if (treinoPlanejado.getEtapas() != null) {
            return treinoPlanejado.getEtapas().stream()
                    .map(e -> e.getDistanciaKm() == null ? 0.0 : e.getDistanciaKm().doubleValue())
                    .mapToDouble(Double::doubleValue)
                    .sum();
        }

        return 0.0;

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

//            PlanoSemanalLlmDto planoDto = iaService.gerarPlanoSemanal(
//                    atletaMapper.toOutputDto(dadosPlanoDto.atleta()),
//                    dadosPlanoDto.ultimosTreinos(),
//                    dadosPlanoDto.planoAnterior());

            PlanoSemanalLlmDto planoDto = iaService.geraPlanoSemanalAvancado(dadosPlanoDto.atleta(), dadosPlanoDto.metaDados(), null);

            validaPlanoGerado(planoDto);
            return planoDto;
        } catch (LLMException e) {
            log.error("Falha na IA ao gerar o plano para o atleta: {}", dadosPlanoDto.atleta().getId());
            throw e;
//        }catch (JsonProcessingException e){
//            log.error("Erro de JSON ao processar o plano para o atleta: {}", atleta.getId(), e);
        } catch (Exception e) {
            log.error("Erro inesperado ao gerar o plano para o atleta: {}", dadosPlanoDto.atleta().getId(), e);
        }
        return null;
    }

    private void validaPlanoGerado(PlanoSemanalLlmDto planoDto) {

        if (planoDto == null) {
            throw new LLMException("IA retornou plano nulo");
        }

        if (planoDto.treinosPlanejados() == null || planoDto.treinosPlanejados().isEmpty()) {
            throw new LLMException("IA retornou plano sem treinos");
        }

    }

    private LocalDate calcularDataTreino(LocalDate semanaInicio, DiaSemana diaSemana) {
        DayOfWeek dayOfWeek = Utils.converterParaDayOfWeek(diaSemana);

        return semanaInicio.with(TemporalAdjusters.nextOrSame(dayOfWeek));
    }


    @Override
    @Transactional
    public void deletePlanoSemanal(UUID planoSemanalId) {
        PlanoSemanal plano = planoSemanalRepository.findById(planoSemanalId)
                .orElseThrow(() -> new ResourceNotFoundException("Plano não encontrado: " + planoSemanalId));

        // Limpa a referência bidirecional em PlanoMetaDados antes de deletar
        PlanoMetaDados metaDados = plano.getPlanoMetaDados();
        if (metaDados != null && metaDados.getPlanoSemanalAtual() != null
                && metaDados.getPlanoSemanalAtual().getId().equals(planoSemanalId)) {
            metaDados.setPlanoSemanalAtual(null);
            planoMetadadosRepository.save(metaDados);
        }

        planoSemanalRepository.delete(plano);
    }

    @Transactional
    public PlanoSemanalOutputDto buscarPlanoPorAtleta(UUID atletaId) {
        PlanoSemanal planoSemanal = planoSemanalRepository.findByAtletaId(atletaId).orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado: " + atletaId));
        Hibernate.initialize(planoSemanal.getTreinosPlanejados());
        return planoSemanalMapper.toOutputDto(planoSemanal);
    }

    private void atualizarVolumeMedio(PlanoSemanal plano) {
        PlanoMetaDados metaDados = plano.getPlanoMetaDados();
        if (metaDados == null) {
            metaDados = new PlanoMetaDados();
            plano.setPlanoMetaDados(metaDados);
        }

        // Busca o volume REAL da semana (treinos realizados)
        double volumeRealizado = calcularVolumeRealizado(plano);

        BigDecimal volumeAtual = metaDados.getVolumeSemanalMedio() != null
                ? metaDados.getVolumeSemanalMedio()
                : BigDecimal.ZERO;

        // Média móvel ponderada (70% histórico + 30% semana atual)
        BigDecimal novoVolumeMedio = volumeAtual
                .multiply(BigDecimal.valueOf(0.7))
                .add(BigDecimal.valueOf(volumeRealizado).multiply(BigDecimal.valueOf(0.3)));

        metaDados.setVolumeSemanalMedio(novoVolumeMedio);
    }

    private double calcularVolumeRealizado(PlanoSemanal plano) {
        return treinoRealizadoRepository
                .findByAtletaIdAndDataTreinoBetween(
                        plano.getAtleta().getId(),
                        plano.getSemanaInicio(),  // ← Semana DO plano
                        plano.getSemanaFim()      // ← Semana DO plano
                )
                .stream()
                .mapToDouble(treino -> treino.getDistanciaKm() != null
                        ? treino.getDistanciaKm().doubleValue()
                        : 0.0)
                .sum();
    }

    private void atualizarRampRate(PlanoMetaDados metaDados, double volumeNovo) {
        if (metaDados.getVolumeSemanalMedio() != null &&
                metaDados.getVolumeSemanalMedio().doubleValue() > 0) {

            double volumeMedio = metaDados.getVolumeSemanalMedio().doubleValue();
            double rampRate = ((volumeNovo - volumeMedio) / volumeMedio) * 100;
            metaDados.setRampRateAtual(rampRate);
        }
    }

    private void atualizarProgressao(PlanoMetaDados metaDados, double volumeNovo) {
        if (metaDados.getVolumeSemanalMedio() != null) {
            if (volumeNovo > metaDados.getVolumeSemanalMedio().doubleValue()) {
                Integer semanas = metaDados.getSemanasProgressaoContinua();
                metaDados.setSemanasProgressaoContinua(semanas != null ? semanas + 1 : 1);
            } else {
                metaDados.setSemanasProgressaoContinua(0);
            }
        }
    }

    private LocalDate calcularDataTreino(LocalDate semanaInicio, DiaSemana diaSemana, List<DiaSemana> diasDisponiveis) {
        LocalDate hoje = LocalDate.now();
        DayOfWeek dayOfWeek = Utils.converterParaDayOfWeek(diaSemana);

        // Se semanaInicio é no passado ou hoje, ajusta para começar do próximo dia disponível
        if (!semanaInicio.isAfter(hoje)) {
            return calcularProximoDiaDisponivel(hoje, diaSemana, diasDisponiveis);
        }

        // Se semanaInicio é no futuro, calcula normalmente a partir da segunda-feira
        return semanaInicio.with(TemporalAdjusters.nextOrSame(dayOfWeek));
    }

    private LocalDate calcularProximoDiaDisponivel(LocalDate dataReferencia, DiaSemana diaSemanaAlvo, List<DiaSemana> diasDisponiveis) {
        DayOfWeek diaReferencia = dataReferencia.getDayOfWeek();
        DayOfWeek diaAlvo = Utils.converterParaDayOfWeek(diaSemanaAlvo);

        // Ordena os dias disponíveis
        List<DayOfWeek> diasOrdenados = diasDisponiveis.stream()
                .map(Utils::converterParaDayOfWeek)
                .sorted(Comparator.comparingInt(DayOfWeek::getValue))
                .toList();

        // Encontra o próximo dia disponível APÓS a data de referência
        Optional<DayOfWeek> proximoDia = diasOrdenados.stream()
                .filter(dia -> dia.getValue() > diaReferencia.getValue())
                .findFirst();

        if (proximoDia.isPresent()) {
            // Próximo dia disponível na mesma semana
            return dataReferencia.with(TemporalAdjusters.next(proximoDia.get()));
        } else {
            // Se não há mais dias na semana, pega o primeiro da próxima semana
            DayOfWeek primeiroDia = diasOrdenados.get(0);
            return dataReferencia.with(TemporalAdjusters.next(primeiroDia));
        }
    }
}



