package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.DecisaoProgressao;
import br.com.menthoros.backend.dto.ProgressaoHistoricoResumo;
import br.com.menthoros.backend.dto.input.DadosPlanoDto;
import br.com.menthoros.backend.dto.output.PlanoSemanalOutputDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.RevisaoSemanal;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ModoGeracaoPlano;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.PlanoJaExistenteException;
import br.com.menthoros.backend.mapper.PlanoSemanalMapper;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.PlanoMetadadosService;
import br.com.menthoros.backend.services.ProgressaoTreinoService;
import br.com.menthoros.backend.services.prompt.WeeklyReviewPromptProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fase 1 da geração de plano (refactor-llm-call-outside-transaction, design.md D1): lê tudo que o
 * prompt e a persistência vão precisar, numa transação que dura milissegundos e termina ANTES da
 * chamada ao LLM — nenhuma conexão do pool fica em posse enquanto o modelo pensa.
 *
 * <p>É um colaborador, não um método privado do {@code PlanoServiceImpl}, porque o
 * {@code @Transactional} do Spring só funciona atravessando o proxy: chamada interna à mesma
 * classe não abre transação.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanGenerationContextLoader {

    private static final int JANELA_HISTORICO_DIAS = 42;

    private final AtletaRepository atletaRepository;
    private final PlanoMetadadosService planoMetadadosService;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final TreinoMapper treinoMapper;
    private final PlanoSemanalRepository planoSemanalRepository;
    private final PlanoSemanalMapper planoSemanalMapper;
    private final ProgressaoTreinoService progressaoTreinoService;
    private final WeeklyReviewPromptProvider weeklyReviewPromptProvider;

    /**
     * Carrega o contexto da geração e inicializa todo caminho lazy lido depois da fronteira.
     *
     * <p>Também aplica a checagem de plano ativo na semana alvo <b>antes</b> de gastar a chamada
     * ao LLM (design.md D3, camada 1); o persister re-checa dentro da transação de escrita e o
     * índice parcial da V52 é a autoridade final.
     *
     * Idempotent: NÃO — {@code buscarOuCriarMetadados} pode inserir a linha de metadados, e ela
     *   sobrevive a uma falha posterior do LLM (design.md D1, decisão do founder em 2026-09-01:
     *   é idempotente por construção e evita o rollback tardio que causou o incidente do cache).
     * Side Effects: possível INSERT em tb_plano_metadados.
     * Tenant-aware: YES — atleta resolvido por {@code findByIdAndTenantId}.
     *
     * @throws DomainNotFoundException      atleta inexistente ou de outro tenant
     * @throws DomainRuleViolationException atleta inativo ou sem dados mínimos
     * @throws PlanoJaExistenteException    já há plano ativo na semana alvo
     */
    @Transactional
    public PlanGenerationContext load(UUID atletaId, ModoGeracaoPlano modoGeracao) {
        UUID tenantId = TenantContext.getRequiredTenantId();

        DadosPlanoDto dados = prepararDadosPlano(atletaId, tenantId);
        Atleta atleta = dados.atleta();

        // Tudo que é lido depois da fronteira (prompt, redistribuição, criarPlanoEntity, shadow do
        // planner) — manter alinhado com a tabela do design.md D2 e com o PlanGenerationContextLoaderIT.
        Hibernate.initialize(atleta.getProvas());
        Hibernate.initialize(atleta.getDiasDisponiveis());
        Hibernate.initialize(atleta.getAssessoria());

        DecisaoProgressao decisaoProgressao = calcularDecisaoProgressao(atletaId);

        // Resolvida UMA vez e repassada: recalcular depois do LLM (que pode levar dezenas de
        // segundos e, em lote, esperar no semáforo) usaria um LocalDate.now() diferente.
        LocalDate semanaInicio = calcularSemanaInicio(atletaId, LocalDate.now(), modoGeracao);

        if (planoSemanalRepository.existePlanoAtivoNaSemana(atletaId, semanaInicio, tenantId)) {
            log.debug("Plano ativo já existe para atleta {} na semana {} — geração abortada antes do LLM",
                    atletaId, semanaInicio);
            throw new PlanoJaExistenteException(
                    "Já existe um plano semanal ativo para o atleta " + atletaId +
                            " iniciando em " + semanaInicio + ". Não é possível gerar planos duplicados.");
        }

        // A mesma revisão alimenta o prompt e o vínculo gravado no plano (add-weekly-review-llm-focus
        // D9/D11); resolver nos dois pontos abriria janela para o LLM ver uma e o plano registrar outra.
        RevisaoSemanal revisaoConsumida = weeklyReviewPromptProvider
                .resolverParaGeracao(atletaId, tenantId, semanaInicio)
                .orElse(null);

        Prova proximaProva = buscarProximaProva(atleta).orElse(null);
        if (proximaProva == null) {
            log.warn("Atleta {} não possui provas futuras cadastradas — plano gerado sem prova alvo", atletaId);
        }

        return new PlanGenerationContext(dados, decisaoProgressao, semanaInicio, revisaoConsumida, proximaProva);
    }

    /**
     * Semana de início do próximo plano: a segunda-feira corrente em {@code SEMANA_ATUAL}; nos
     * demais modos, uma semana após o último plano (ou a próxima segunda, se ele já passou).
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE.
     * Tenant-aware: NO — recebe o atleta já resolvido pelo chamador.
     */
    public LocalDate calcularSemanaInicio(UUID atletaId, LocalDate hoje, ModoGeracaoPlano modoGeracao) {
        LocalDate segundaFeiraSemanaAtual = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate segundaFeiraSemanaProxima = hoje.with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        if (ModoGeracaoPlano.SEMANA_ATUAL.equals(modoGeracao)) {
            return segundaFeiraSemanaAtual;
        }

        return planoSemanalRepository
                .findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)
                .map(p -> {
                    LocalDate proximaDataPlano = p.getSemanaInicio().plusWeeks(1);
                    return proximaDataPlano.isBefore(segundaFeiraSemanaProxima)
                            ? segundaFeiraSemanaProxima
                            : proximaDataPlano;
                })
                .orElse(segundaFeiraSemanaProxima);
    }

    private DadosPlanoDto prepararDadosPlano(UUID atletaId, UUID tenantId) {
        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado"));

        validarEstadoAtleta(atleta);

        PlanoMetaDados metaDados = planoMetadadosService.buscarOuCriarMetadados(atleta);

        LocalDate hoje = LocalDate.now();
        LocalDate inicio42d = hoje.minusDays(JANELA_HISTORICO_DIAS);
        // D8: cancelado não conta na carga — mesmo predicado usado por TsbService/produtores.
        List<TreinoRealizado> realizados = treinoRealizadoRepository
                .findByAtletaIdAndDataTreinoBetween(atletaId, inicio42d, hoje).stream()
                .filter(TreinoRealizado::contaNaCarga)
                .toList();

        List<TreinoRealizadoOutputDto> ultimosTreinos = realizados.stream()
                .map(treinoMapper::toOutputDto)
                .toList();

        LocalDate dataInicio = planoSemanalRepository
                .findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)
                .map(p -> p.getSemanaInicio().plusWeeks(1))
                .orElse(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));

        PlanoSemanalOutputDto planoAnterior = planoSemanalRepository
                .findTopByAtletaIdAndSemanaInicioBeforeAndStatusOrderBySemanaInicioDesc(
                        atletaId, dataInicio, PlanoStatus.CONCLUIDO)
                .map(planoSemanalMapper::toOutputDto)
                .orElse(null);

        return new DadosPlanoDto(atleta, dataInicio, planoAnterior, ultimosTreinos, metaDados);
    }

    private void validarEstadoAtleta(Atleta atleta) {
        if (atleta.getAtivo() == null || !atleta.getAtivo().isActive()) {
            throw new DomainRuleViolationException(
                    "Não é possível gerar plano para atleta inativo. Status: " +
                    (atleta.getAtivo() != null ? atleta.getAtivo().getLabel() : "INDEFINIDO"));
        }
        if (atleta.getDiasDisponiveis() == null || atleta.getDiasDisponiveis().isEmpty()) {
            throw new DomainRuleViolationException(
                    "Não é possível gerar plano sem dias disponíveis. " +
                    "Por favor, configure os dias disponíveis para treino no perfil do atleta.");
        }
        if (atleta.getObjetivo() == null || atleta.getObjetivo().isBlank()) {
            throw new DomainRuleViolationException(
                    "Não é possível gerar plano sem objetivo definido. " +
                    "Configure o objetivo no perfil do atleta.");
        }
        if (atleta.getNivelExperiencia() == null) {
            throw new DomainRuleViolationException(
                    "Não é possível gerar plano sem nível de experiência definido. " +
                    "Configure o nível no perfil do atleta.");
        }
        log.debug("Atleta {} validado com sucesso: {} dias disponíveis, nível: {}",
                atleta.getId(), atleta.getDiasDisponiveis().size(), atleta.getNivelExperiencia());
    }

    private DecisaoProgressao calcularDecisaoProgressao(UUID atletaId) {
        try {
            ProgressaoHistoricoResumo historico = progressaoTreinoService.calcularHistorico(atletaId);
            return progressaoTreinoService.calcularDecisao(historico);
        } catch (DomainNotFoundException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Falha ao calcular decisão de progressão para atleta {} — plano será gerado sem contexto de progressão", atletaId, e);
            return null;
        }
    }

    /**
     * Próxima prova futura do atleta: a marcada como alvo ou, na falta dela, a de data mais próxima.
     */
    private Optional<Prova> buscarProximaProva(Atleta atleta) {
        LocalDate hoje = LocalDate.now();
        List<Prova> provasFuturas = atleta.getProvas() == null
                ? Collections.emptyList()
                : atleta.getProvas().stream()
                        .filter(p -> p.getDataProva() != null && !p.getDataProva().isBefore(hoje))
                        .filter(prova -> prova.getStatusProva() != ProvaStatus.CANCELADA)
                        .sorted(Comparator.comparing(Prova::getDataProva))
                        .toList();

        if (provasFuturas.isEmpty()) {
            return Optional.empty();
        }
        return provasFuturas.stream()
                .filter(Prova::isProvaAlvo)
                .findFirst()
                .or(() -> provasFuturas.stream().findFirst());
    }
}
