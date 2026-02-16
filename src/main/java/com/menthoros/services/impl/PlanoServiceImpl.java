package com.menthoros.services.impl;

import com.menthoros.dto.input.DadosPlanoDto;
import com.menthoros.dto.llm.PlanoSemanalLlmDto;
import com.menthoros.dto.llm.TreinoPlanejadoLlmDto;
import com.menthoros.dto.output.PadroesTreino;
import com.menthoros.dto.output.PlanoSemanalOutputDto;
import com.menthoros.dto.output.TreinoRealizadoOutputDto;
import com.menthoros.entity.*;
import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.ModoGeracaoPlano;
import com.menthoros.enums.PlanoStatus;
import com.menthoros.exception.DomainNotFoundException;
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
import com.menthoros.services.*;
import com.menthoros.services.helper.RedistribuicaoTreinoHelper;
import com.menthoros.services.helper.RegraGeracaoTreino;
import com.menthoros.util.Utils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class PlanoServiceImpl implements PlanoService {

    // Constantes de configuração de volume e progressão
    private static final BigDecimal FATOR_INCREMENTO_VOLUME_PLANEJADO = BigDecimal.valueOf(1.10); // 10% de incremento
    private static final BigDecimal PESO_VOLUME_HISTORICO = BigDecimal.valueOf(0.7); // 70% peso histórico
    private static final BigDecimal PESO_VOLUME_ATUAL = BigDecimal.valueOf(0.3); // 30% peso atual
    private static final int DIAS_POR_SEMANA = 6;
    private static final int LIMITE_TREINOS_HISTORICO = 7;

    private final IaService iaService;
    private final AtletaRepository atletaRepository;
    private final AtletaMapper atletaMapper;
    private final TreinoMapper treinoMapper;
    private final PlanoSemanalMapper planoSemanalMapper;
    private final PlanoMetadadosRepository planoMetadadosRepository;
    private final PlanoMetadadosService planoMetadadosService;
    private final TsbService tsbService;

    private final EmbeddingService embeddingService;
    private final PlanoSemanalRepository planoSemanalRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final RedistribuicaoTreinoHelper redistribuicaoHelper;
    private final RegraGeracaoTreino regraGeracaoTreino;
    private final MetricasAlertaService metricasAlertaService;
    private final MetricasAgregadasServiceImpl metricasAgregadasService;

    /**
     * Gera um plano de treino semanal personalizado para um atleta usando IA.
     *
     * <p>Este método coordena todo o processo de geração de plano, incluindo:
     * <ul>
     *   <li>Preparação dos dados do atleta e histórico de treinos</li>
     *   <li>Geração do plano através do serviço de IA</li>
     *   <li>Redistribuição de treinos conforme o modo de geração</li>
     *   <li>Persistência do plano completo no banco de dados</li>
     * </ul>
     *
     * <p>O modo de geração determina o comportamento da redistribuição:
     * <ul>
     *   <li>{@link ModoGeracaoPlano#SEMANA_ATUAL}: Redistribui treinos considerando dias já passados</li>
     *   <li>{@link ModoGeracaoPlano#PROXIMA_SEMANA}: Usa os treinos gerados pela LLM diretamente</li>
     * </ul>
     *
     * @param atletaId ID do atleta para o qual o plano será gerado
     * @param modoGeracao modo que determina como os treinos serão distribuídos na semana
     * @return o plano semanal gerado e persistido
     * @throws DomainNotFoundException se o atleta não for encontrado
     * @throws LLMException se houver falha na geração do plano pela IA ou se o plano retornado for inválido
     * @throws DomainRuleViolationException se não for possível gerar treinos (ex: sem dias disponíveis)
     * @see ModoGeracaoPlano
     * @see PlanoSemanal
     */
    @Transactional
    @Override
    public PlanoSemanal gerarPlanoTreino(UUID atletaId, ModoGeracaoPlano modoGeracao) {
        // Validação de entrada
        validarParametrosEntrada(atletaId, modoGeracao);

        DadosPlanoDto dadosPlano = getPreparaDadosPlano(atletaId);
        Hibernate.initialize(dadosPlano.atleta().getProvas()); // evita LazyInitializationException

        try {
            PlanoSemanalLlmDto planoDto = gerarPlanoSemanal(dadosPlano);

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

    /**
     * Persiste um plano completo gerado pela LLM aplicando o princípio SRP.
     *
     * <p>Este método orquestra a persistência do plano delegando cada responsabilidade
     * a métodos especializados, seguindo o Single Responsibility Principle:
     * <ol>
     *   <li><strong>Cálculo de período:</strong> Define início e fim da semana do plano</li>
     *   <li><strong>Obtenção de treinos:</strong> Redistribui conforme o modo de geração</li>
     *   <li><strong>Preparação de metadados:</strong> Atualiza TSB, ramp rate e progressão</li>
     *   <li><strong>Criação de entidades:</strong> Monta estrutura completa do plano</li>
     *   <li><strong>Persistência:</strong> Salva plano e metadados no banco de dados</li>
     * </ol>
     *
     * @param planoDto DTO gerado pela LLM contendo treinos e métricas planejadas
     * @param dadosPlano dados consolidados do atleta, histórico e metadados
     * @param modoGeracao modo que determina a estratégia de redistribuição de treinos
     * @return plano semanal persistido com todas as associações carregadas
     * @throws DomainRuleViolationException se não for possível gerar treinos após redistribuição
     */
    private PlanoSemanal persistirPlanoCompleto(PlanoSemanalLlmDto planoDto, DadosPlanoDto dadosPlano, ModoGeracaoPlano modoGeracao) {
        Atleta atleta = dadosPlano.atleta();
        LocalDate hoje = LocalDate.now();

        log.info("Iniciando persistência de plano completo para atleta {}", atleta.getId());

        // 1. Calcular período do plano
        LocalDate semanaInicio = calcularSemanaInicio(atleta.getId(), hoje, modoGeracao);
        PeriodoPlano periodo = new PeriodoPlano(semanaInicio);

        // ** Adição da verificação de duplicidade **
        planoSemanalRepository.findByAtletaIdAndSemanaInicio(atleta.getId(), semanaInicio)
                .ifPresent(existingPlano -> {
                    log.debug("Tentativa de gerar plano duplicado para atleta {} na semana de início {}. Plano existente ID: {}", atleta.getId(), semanaInicio, existingPlano.getId());
                    throw new DomainRuleViolationException(
                            "Já existe um plano semanal para o atleta " + atleta.getId() +
                                    " iniciando em " + semanaInicio + ". Não é possível gerar planos duplicados."
                    );
                });
        // ** Fim da adição **

        log.info("Período calculado: {} a {} (Modo: {}, {} treinos no plano LLM)",
                periodo.inicio(), periodo.fim(), modoGeracao, planoDto.treinosPlanejados().size());

        // 2. Obter treinos (com ou sem redistribuição conforme o modo)
        List<TreinoPlanejadoLlmDto> treinos = obterTreinosParaPlano(
                planoDto.treinosPlanejados(),
                atleta,
                periodo,
                modoGeracao
        );

        // 3. Preparar metadados
        PlanoMetaDados metaDados = prepararMetadados(planoDto, dadosPlano);

        // 4. Criar plano completo com treinos
        PlanoSemanal plano = criarPlanoComTreinos(planoDto, atleta, periodo, metaDados, treinos);

        // 5. Persistir e retornar
        return salvarPlanoCompleto(plano, metaDados);
    }

    /**
     * Record que encapsula o período (início e fim) de um plano semanal.
     *
     * <p>Facilita a passagem desses dados entre métodos e melhora a legibilidade do código.
     * O construtor secundário calcula automaticamente a data fim somando 6 dias à data de início.
     *
     * @param inicio data de início do plano semanal (tipicamente uma segunda-feira)
     * @param fim data de fim do plano semanal (tipicamente um domingo)
     */
    private record PeriodoPlano(LocalDate inicio, LocalDate fim) {

        /**
         * Construtor de conveniência que calcula automaticamente a data fim.
         *
         * @param inicio data de início do plano semanal
         */
        PeriodoPlano(LocalDate inicio) {
            this(inicio, inicio.plusDays(DIAS_POR_SEMANA));
        }
    }
    /**
     * Calcula a data de início da próxima semana de treino para um atleta.
     *
     * <p>Se o atleta já possui planos anteriores, a data de início será uma semana após
     * o início do último plano. Caso contrário, será a segunda-feira da semana atual ou anterior.
     *
     * @param atletaId identificador único do atleta
     * @param hoje data atual para referência de cálculo
     * @param modoGeracao modo de geração do plano (não utilizado atualmente)
     * @return data de início calculada para o novo plano
     */
    private LocalDate calcularSemanaInicio(UUID atletaId, LocalDate hoje, ModoGeracaoPlano modoGeracao) {
        return planoSemanalRepository
                .findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)
                .map(p -> {
                    // Se o último plano já passou, gera para a próxima semana
                    LocalDate proximaSemana = hoje.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
                    if (p.getSemanaInicio().plusWeeks(1).isBefore(proximaSemana)) {
                        return proximaSemana;
                    }
                    // Caso contrário, segue a lógica padrão
                    return p.getSemanaInicio().plusWeeks(1);
                })
                .orElse(hoje.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)));
    }

    /**
     * Obtém a lista de treinos para o plano, aplicando redistribuição se necessário.
     * Para SEMANA_ATUAL, redistribui treinos considerando dias já passados.
     * Para outros modos, usa os treinos gerados pela LLM diretamente.
     */
    private List<TreinoPlanejadoLlmDto> obterTreinosParaPlano(
            List<TreinoPlanejadoLlmDto> treinosLlm,
            Atleta atleta,
            PeriodoPlano periodo,
            ModoGeracaoPlano modoGeracao) {

        List<TreinoPlanejadoLlmDto> treinos = ModoGeracaoPlano.SEMANA_ATUAL.equals(modoGeracao)
                ? redistribuicaoHelper.redistribuirTreinos(
                        treinosLlm,
                        atleta.getDiasDisponiveis(),
                        LocalDate.now(),
                        periodo.inicio(),
                        periodo.fim(),
                        modoGeracao
                )
                : treinosLlm;

        validarTreinosGerados(treinos);
        return treinos;
    }

    /**
     * Valida se há treinos após redistribuição ou geração.
     * Lança exceção se a lista estiver vazia.
     */
    private void validarTreinosGerados(List<TreinoPlanejadoLlmDto> treinos) {
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
    }

    /**
     * Cria a entidade PlanoSemanal completa com treinos planejados e volumes calculados.
     */
    private PlanoSemanal criarPlanoComTreinos(
            PlanoSemanalLlmDto planoDto,
            Atleta atleta,
            PeriodoPlano periodo,
            PlanoMetaDados metaDados,
            List<TreinoPlanejadoLlmDto> treinosDto) {

        PlanoSemanal plano = criarPlanoEntity(planoDto, atleta, periodo.inicio(), periodo.fim(), metaDados);

        List<TreinoPlanejado> treinosPlanejados = converterTreinos(treinosDto, plano, periodo.inicio());

        BigDecimal volumePlanejado = calcularVolumeTotalPlanejado(treinosPlanejados);

        plano.setVolumePlanejadoKm(volumePlanejado);
        plano.setVolumeAlvoKm(volumePlanejado);
        plano.setTreinosPlanejados(treinosPlanejados);

        return plano;
    }

    /**
     * Converte lista de DTOs de treinos em entidades TreinoPlanejado.
     */
    private List<TreinoPlanejado> converterTreinos(
            List<TreinoPlanejadoLlmDto> treinosDto,
            PlanoSemanal plano,
            LocalDate semanaInicio) {

        return treinosDto.stream()
                .map(dto -> converterTreino(dto, plano, semanaInicio))
                .toList();
    }

    /**
     * Converte um DTO de treino em entidade TreinoPlanejado,
     * calculando a data do treino com base no dia da semana.
     */
    private TreinoPlanejado converterTreino(
            TreinoPlanejadoLlmDto dto,
            PlanoSemanal plano,
            LocalDate semanaInicio) {

        TreinoPlanejado treino = treinoMapper.toEntity(dto);
        treino.setPlanoSemanal(plano);

        DiaSemana diaSemana = DiaSemana.valueOf(dto.diaSemana());
        LocalDate dataTreino = calcularDataTreino(semanaInicio, diaSemana);
        treino.setDataTreino(dataTreino);

        return treino;
    }

    /**
     * Calcula o volume total planejado somando as distâncias de todos os treinos.
     */
    private BigDecimal calcularVolumeTotalPlanejado(List<TreinoPlanejado> treinos) {
        double volume = treinos.stream()
                .mapToDouble(this::distanciaTreinoPlanejado)
                .sum();
        return BigDecimal.valueOf(volume);
    }

    /**
     * Persiste o plano completo e atualiza os metadados.
     */
    private PlanoSemanal salvarPlanoCompleto(PlanoSemanal plano, PlanoMetaDados metaDados) {
        // PlanoMetaDados is already persisted in prepararMetadados(), no need to save again
        PlanoSemanal planoSalvo = planoSemanalRepository.save(plano);

        log.info("✅ Plano salvo - {} treinos, volume: {}km",
                plano.getTreinosPlanejados().size(),
                plano.getVolumePlanejadoKm());

        return planoSalvo;
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

            var volumePlanejado = mediaSemanalHistorica.multiply(FATOR_INCREMENTO_VOLUME_PLANEJADO);

            metaDados.setVolumePlanejado(volumePlanejado);
            metaDados.setVolumeSemanalMedio(mediaSemanalHistorica);

        }

        plano.setPlanoMetaDados(metaDados);

        return plano;
    }

    /**
     * Prepara e atualiza metadados do plano baseado em treinos realizados e plano gerado.
     *
     * <p>Este método orquestra a atualização de todas as métricas do atleta:
     * <ul>
     *   <li>Métricas de carga (CTL, ATL, TSB) - calculadas pelo {@link TsbService}</li>
     *   <li>Métricas semanais médias (volume, TSS, frequência)</li>
     *   <li>Padrões de treino (dias consecutivos, desde último descanso)</li>
     *   <li>Progressão de volume</li>
     * </ul>
     *
     * <p><b>IMPORTANTE:</b> As métricas CTL/ATL/TSB são calculadas baseadas em treinos
     * REALIZADOS (não planejados), garantindo precisão e consistência.
     *
     * @param planoDto Plano gerado pela IA (contém volume planejado)
     * @param dadosPlano Dados do plano incluindo metadados anteriores
     * @return Metadados atualizados e persistidos
     */
    private PlanoMetaDados prepararMetadados(PlanoSemanalLlmDto planoDto, DadosPlanoDto dadosPlano) {
        PlanoMetaDados metaDados = dadosPlano.metaDados();

        if (metaDados.getId() == null) {
            log.debug("Metadados sem ID - retornando sem atualizar");
            return metaDados;
        }

        Atleta atleta = dadosPlano.atleta();
        UUID atletaId = atleta.getId();

        log.debug("Atualizando metadados para atleta {} com volume planejado: {}km",
            atletaId, planoDto.volumePlanejadoKm());

        // 1. Calcular métricas semanais médias baseadas em treinos realizados
        com.menthoros.dto.output.MetricasSemanaisMedias metricas =
            metricasAgregadasService.calcularMetricasSemanais(atletaId, 6);

        metaDados.setVolumeSemanalMedio(metricas.volumeMedio());
        metaDados.setTssSemanalMedio(metricas.tssMedio());
        metaDados.setTreinosPorSemanaMedio(metricas.treinosPorSemanaMedio());

        log.debug("Métricas semanais atualizadas - Volume: {}km, TSS: {}, Frequência: {} treinos/semana",
            metricas.volumeMedio(), metricas.tssMedio(), metricas.treinosPorSemanaMedio());

        // 2. Calcular padrões de treino (dias consecutivos e desde último descanso)
        PadroesTreino padroes =
            metricasAgregadasService.calcularPadroesTreino(atletaId);

        metaDados.setDiasConsecutivosTreino(padroes.diasConsecutivos());
        metaDados.setDiasDesdeUltimoDescanso(padroes.diasDesdeDescanso());

        log.debug("Padrões de treino atualizados - Dias consecutivos: {}, Desde descanso: {}",
            padroes.diasConsecutivos(), padroes.diasDesdeDescanso());

        // 3. Atualizar volume planejado desta semana
        metaDados.setVolumePlanejado(BigDecimal.valueOf(planoDto.volumePlanejadoKm()));

        // 4. Atualizar progressão de volume
        atualizarProgressao(metaDados, planoDto.volumePlanejadoKm());

        // 5. Analisar métricas e aplicar alertas/status/recomendação
        metaDados.aplicarAnalise(metricasAlertaService.analisarMetricas(metaDados));

        // 6. Persistir metadados atualizados
        metaDados = planoMetadadosRepository.save(metaDados);

        log.info("Metadados atualizados com sucesso para atleta {}", atletaId);

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
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado"));

        // Validações de negócio do atleta
        validarEstadoAtleta(atleta);

        PlanoMetaDados metaDados = planoMetadadosService.buscarOuCriarMetadados(atleta);

        List<TreinoRealizado> realizados = treinoRealizadoRepository
                .findByAtletaIdOrderByDataTreinoDesc(atletaId);

        List<TreinoRealizadoOutputDto> ultimosTreinos = realizados.isEmpty()
                ? Collections.emptyList()
                : realizados.stream()
                .limit(LIMITE_TREINOS_HISTORICO)
                .map(treinoMapper::toOutputDto)
                .toList();

        LocalDate dataInicio = planoSemanalRepository
                .findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)
                .map(p -> p.getSemanaInicio().plusWeeks(1))
                .orElse(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));

        PlanoSemanalOutputDto planoAnterior = planoSemanalRepository.findTopByAtletaIdAndSemanaInicioBeforeAndStatusOrderBySemanaInicioDesc(atletaId, dataInicio, PlanoStatus.CONCLUIDO).map(planoSemanalMapper::toOutputDto).orElse(null);

        return new DadosPlanoDto(atleta, dataInicio, planoAnterior, ultimosTreinos, metaDados);
    }

    private PlanoSemanalLlmDto gerarPlanoSemanal(DadosPlanoDto dadosPlanoDto) {
        try {
            log.info("Iniciando geração de plano para atleta: {}", dadosPlanoDto.atleta().getId());

            PlanoSemanalLlmDto planoDto = iaService.geraPlanoSemanalAvancado(dadosPlanoDto.atleta(), dadosPlanoDto.metaDados(), null);

            validaPlanoGerado(planoDto);
            return planoDto;
        } catch (LLMException e) {
            log.error("Falha na IA ao gerar o plano para o atleta: {}", dadosPlanoDto.atleta().getId());
            throw e;
        } catch (Exception e) {

            log.error("Erro inesperado ao gerar o plano para o atleta: {}", dadosPlanoDto.atleta().getId(), e);
            throw new LLMException("Erro inesperado ao gerar plano", e);
        }
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

        // Initialize lazy-loaded TreinosPlanejados to allow cascade deletion of child entities
        Hibernate.initialize(plano.getTreinosPlanejados());

        // Note: Do NOT initialize planoMetaDados collection. Let Hibernate's orphanRemoval handle the bidirectional cleanup.
        // Initializing the collection during deletion causes Hibernate to confuse object states.

        planoSemanalRepository.delete(plano);
        log.info("✅ Plano deletado com sucesso - ID: {}", planoSemanalId);
    }


    @Transactional
    public PlanoSemanalOutputDto buscarPlanoPorAtleta(UUID atletaId) {
        PlanoSemanal planoSemanal = planoSemanalRepository.findByAtletaId(atletaId).orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado: " + atletaId));
        Hibernate.initialize(planoSemanal.getTreinosPlanejados());
        return planoSemanalMapper.toOutputDto(planoSemanal);
    }

    /**
     * Atualiza contador de semanas de progressão contínua.
     *
     * <p>Incrementa o contador quando o volume planejado excede a média semanal,
     * resetando quando há redução de volume. Importante para identificar
     * necessidade de semana de recuperação (após 3-4 semanas consecutivas de aumento).
     *
     * @param metaDados Metadados a serem atualizados
     * @param volumeNovo Volume do novo plano (km)
     */
    private void atualizarProgressao(PlanoMetaDados metaDados, double volumeNovo) {
        if (metaDados.getVolumeSemanalMedio() != null) {
            if (volumeNovo > metaDados.getVolumeSemanalMedio().doubleValue()) {
                Integer semanas = metaDados.getSemanasProgressaoContinua();
                Integer novaContagem = semanas != null ? semanas + 1 : 1;
                metaDados.setSemanasProgressaoContinua(novaContagem);

                log.debug("Progressão contínua: {} semanas seguidas de aumento de volume", novaContagem);

                if (novaContagem >= 3) {
                    log.info("Atleta em progressão contínua há {} semanas. Considere semana de recuperação em breve.",
                            novaContagem);
                }
            } else {
                metaDados.setSemanasProgressaoContinua(0);
                log.debug("Progressão resetada - volume não aumentou em relação à média");
            }
        }
    }

    private void validarParametrosEntrada(UUID atletaId, ModoGeracaoPlano modoGeracao) {
        Objects.requireNonNull(atletaId, "ID do atleta é obrigatório");
        Objects.requireNonNull(modoGeracao, "Modo de geração é obrigatório");
    }

    /**
     * Valida o estado do atleta antes de gerar um plano de treino.
     *
     * <p>Verifica se o atleta está em condições de receber um plano, incluindo:
     * <ul>
     *   <li>Se o atleta está ativo no sistema</li>
     *   <li>Se possui dias disponíveis para treinar</li>
     *   <li>Se possui dados essenciais configurados</li>
     * </ul>
     *
     * @param atleta o atleta a ser validado
     * @throws DomainRuleViolationException se o atleta não atender aos requisitos
     */
    private void validarEstadoAtleta(Atleta atleta) {
        // Valida se o atleta está ativo
        if (atleta.getAtivo() == null || !atleta.getAtivo().isActive()) {
            throw new DomainRuleViolationException(
                    "Não é possível gerar plano para atleta inativo. Status: " +
                    (atleta.getAtivo() != null ? atleta.getAtivo().getLabel() : "INDEFINIDO")
            );
        }

        // Valida se o atleta possui dias disponíveis
        if (atleta.getDiasDisponiveis() == null || atleta.getDiasDisponiveis().isEmpty()) {
            throw new DomainRuleViolationException(
                    "Não é possível gerar plano sem dias disponíveis. " +
                    "Por favor, configure os dias disponíveis para treino no perfil do atleta."
            );
        }

        // Valida se possui objetivo definido
        if (atleta.getObjetivo() == null || atleta.getObjetivo().isBlank()) {
            throw new DomainRuleViolationException(
                    "Não é possível gerar plano sem objetivo definido. " +
                    "Configure o objetivo no perfil do atleta."
            );
        }

        // Valida se possui nível de experiência definido
        if (atleta.getNivelExperiencia() == null) {
            throw new DomainRuleViolationException(
                    "Não é possível gerar plano sem nível de experiência definido. " +
                    "Configure o nível no perfil do atleta."
            );
        }

        log.debug("Atleta {} validado com sucesso: {} dias disponíveis, nível: {}",
                atleta.getId(),
                atleta.getDiasDisponiveis().size(),
                atleta.getNivelExperiencia());
    }
}