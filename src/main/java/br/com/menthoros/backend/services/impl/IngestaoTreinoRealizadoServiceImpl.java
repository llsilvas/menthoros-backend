package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.events.TreinoRegistradoEvent;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.IngestaoTreinoRealizadoService;
import br.com.menthoros.backend.services.TsbService;
import br.com.menthoros.backend.services.helper.TreinoDedupHelper;
import br.com.menthoros.backend.services.helper.TssCalculatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Seam único de ingestão de treino realizado (design.md, `ingestao-treino-realizado`).
 *
 * <p>Absorve o pós-processamento hoje reimplementado em cada um dos 10 caminhos de entrada:
 * dedup, {@code tssCalculado}, evento de análise e carga do dia — tudo atrás de duas operações
 * pequenas, {@link #registrar} e {@link #reprocessar}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestaoTreinoRealizadoServiceImpl implements IngestaoTreinoRealizadoService {

    private static final String METODO_DISPOSITIVO = "DISPOSITIVO";

    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final TreinoDedupHelper treinoDedupHelper;
    private final TssCalculatorService tssCalculatorService;
    private final TsbService tsbService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Idempotent: YES — reenviar o mesmo (atleta, externalId) nunca duplica a linha.
     * Side Effects: Database insert/update do treino, das métricas diárias afetadas e do
     * PlanoMetaDados; publicação de evento em inserção nova.
     * Tenant-aware: NO — o tenant é derivado da entidade (@PrePersist).
     */
    @Override
    @Transactional
    public TreinoDedupHelper.SaveResult registrar(TreinoRealizado treino, @Nullable String externalId) {
        validarRegistrar(treino);

        // A entidade já tem id quando o chamador fez find-or-new + merge (re-sync) — nesse caso o
        // save é sempre um UPDATE e nunca conta como inserção nova, independente do que
        // TreinoDedupHelper.SaveResult#inserted() disser sobre a ausência de conflito de unicidade.
        boolean eraNovo = treino.getId() == null;

        // Checagem prévia por (externalId, atletaId) — a constraint única (uk_treino_realizado_
        // external_id_atleta, V23) protege contra a corrida de fato concorrente, mas capturar a
        // violação DENTRO desta transação (@Transactional, D6) não é seguro no Postgres: uma vez
        // que uma instrução falha, a transação inteira fica "aborted" e a própria query de
        // fallback (findByExternalIdAndAtletaId) também falharia com 25P02. Pré-checar evita isso
        // no caminho comum (sequencial); a corrida verdadeiramente concorrente é um risco residual
        // já presente nos chamadores atuais de TreinoDedupHelper (todos @Transactional).
        if (eraNovo && externalId != null) {
            var duplicata = treinoRealizadoRepository.findByExternalIdAndAtletaId(externalId, treino.getAtleta().getId());
            if (duplicata.isPresent()) {
                TreinoRealizado existente = duplicata.get();
                tsbService.recalcularDesde(existente.getAtleta().getId(), existente.getDataTreino());
                log.info("Treino realizado já registrado, ignorando duplicata: id={}, fonte={}",
                        existente.getId(), existente.getFonteDados());
                return new TreinoDedupHelper.SaveResult(existente, false);
            }
        }

        aplicarTssSeNecessario(treino);
        TreinoDedupHelper.SaveResult resultado = salvar(treino, externalId);

        if (eraNovo && resultado.inserted()) {
            eventPublisher.publishEvent(
                    new TreinoRegistradoEvent(resultado.treino().getId(), resultado.treino().getTenantId()));
        }

        tsbService.recalcularDesde(resultado.treino().getAtleta().getId(), resultado.treino().getDataTreino());

        log.info("Treino realizado registrado: id={}, fonte={}, novo={}",
                resultado.treino().getId(), resultado.treino().getFonteDados(), eraNovo && resultado.inserted());
        return resultado;
    }

    /**
     * Idempotent: YES — reprocessar o mesmo treino sem mudança produz o mesmo resultado.
     * Side Effects: Database update do treino (quando recalcula tssCalculado) e das métricas
     * diárias afetadas.
     * Tenant-aware: NO
     */
    @Override
    @Transactional
    public void reprocessar(UUID treinoRealizadoId, @Nullable LocalDate dataAnterior) {
        TreinoRealizado treino = treinoRealizadoRepository.findById(treinoRealizadoId)
                .orElseThrow(() -> new DomainNotFoundException(
                        "Treino realizado não encontrado: " + treinoRealizadoId));

        if (contaNaCarga(treino) && !METODO_DISPOSITIVO.equals(treino.getMetodoCalculoTss())) {
            treino.setTssCalculado(tssCalculatorService.calcularTss(treino));
            treino.setMetodoCalculoTss("CALCULADO");
            treinoRealizadoRepository.save(treino);
        }

        LocalDate dataAlvo = treino.getDataTreino();
        LocalDate menorData = (dataAnterior != null && dataAnterior.isBefore(dataAlvo)) ? dataAnterior : dataAlvo;

        tsbService.recalcularDesde(treino.getAtleta().getId(), menorData);

        log.info("Treino realizado reprocessado: id={}, dataAnterior={}, dataAtual={}",
                treinoRealizadoId, dataAnterior, dataAlvo);
    }

    private void validarRegistrar(TreinoRealizado treino) {
        if (treino.getDataTreino() == null) {
            throw new DomainRuleViolationException("dataTreino não pode ser nulo");
        }
        if (treino.getAtleta() == null) {
            throw new DomainRuleViolationException("atleta não pode ser nulo");
        }
    }

    private void aplicarTssSeNecessario(TreinoRealizado treino) {
        if (treino.getTssCalculado() == null || !METODO_DISPOSITIVO.equals(treino.getMetodoCalculoTss())) {
            treino.setTssCalculado(tssCalculatorService.calcularTss(treino));
            // TssCalculatorService não expõe qual método venceu internamente (FC/PACE/RPE) — a
            // etiqueta genérica basta, já que nenhuma regra de negócio distingue os três; só
            // DISPOSITIVO importa (D3.1).
            treino.setMetodoCalculoTss("CALCULADO");
        }
    }

    private TreinoDedupHelper.SaveResult salvar(TreinoRealizado treino, @Nullable String externalId) {
        if (externalId == null) {
            return new TreinoDedupHelper.SaveResult(treinoRealizadoRepository.save(treino), true);
        }
        // A constraint única (uk_treino_realizado_tenant_fonte_external, V29) só se aplica quando
        // a coluna external_id está preenchida — TreinoDedupHelper.saveIdempotent não a seta, é
        // responsabilidade do chamador (mesmo contrato de FitTreinoPersister/IntervalsIcuActivityMapper).
        treino.setExternalId(externalId);
        return treinoDedupHelper.saveIdempotent(treino, externalId, treino.getAtleta().getId());
    }

    private boolean contaNaCarga(TreinoRealizado treino) {
        return treino.getStatusSincronizacao() != StatusSincronizacao.CANCELADO;
    }
}
