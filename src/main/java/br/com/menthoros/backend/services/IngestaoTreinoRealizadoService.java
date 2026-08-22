package br.com.menthoros.backend.services;

import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.services.helper.TreinoDedupHelper;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Ingestão de treino realizado — o seam único por onde todo {@link TreinoRealizado}, de qualquer
 * {@link br.com.menthoros.backend.enums.FonteDados}, entra no sistema e produz suas consequências:
 * unicidade, {@code tssCalculado}, evento de análise e carga do dia (ver
 * {@code openspec/changes/ingestao-treino-realizado/design.md}).
 *
 * <p>Duas operações para os dois gestos que os chamadores têm: {@link #registrar} para "este
 * treino entrou" e {@link #reprocessar} para "este treino mudou ou saiu".</p>
 */
public interface IngestaoTreinoRealizadoService {

    /**
     * Registra um treino realizado — novo ou já gerenciado (re-sync com merge feito pelo
     * chamador).
     *
     * <p><b>Ordem garantida:</b> calcula {@code tssCalculado} (preservando o valor de dispositivo,
     * D3.1) → persiste de forma idempotente por {@code (atleta, externalId)} quando
     * {@code externalId} não é nulo → publica {@link
     * br.com.menthoros.backend.events.TreinoRegistradoEvent} apenas quando esta chamada inseriu
     * uma linha nova → recalcula a carga de {@code treino.getDataTreino()} até hoje.</p>
     *
     * <p><b>Entidade nova ou gerenciada:</b> se {@code treino.getId()} já está preenchido antes da
     * chamada (o caminho de re-sync fez find-or-new e mesclou os campos), o save é um UPDATE e
     * nenhum evento é publicado, independente do resultado da deduplicação.</p>
     *
     * <p><b>Invariantes de entrada:</b> {@code treino.getDataTreino() != null} (CA8);
     * {@code treino.getAtleta() != null}.</p>
     *
     * Idempotent: YES — reenviar o mesmo (atleta, externalId) nunca duplica a linha.
     * Side Effects: Database insert/update do treino, das métricas diárias afetadas e do
     * PlanoMetaDados; publicação de evento em inserção nova.
     * Tenant-aware: NO — o tenant é derivado da entidade (@PrePersist), nunca do TenantContext.
     *
     * @param treino     o treino a registrar, novo ou já gerenciado
     * @param externalId identificador da fonte externa, para deduplicação; {@code null} para
     *                   fontes sem identificador externo (ex.: lançamento manual)
     * @throws br.com.menthoros.backend.exception.DomainRuleViolationException se
     *                                                                         {@code dataTreino} for nulo (CA8)
     */
    TreinoDedupHelper.SaveResult registrar(TreinoRealizado treino, @Nullable String externalId);

    /**
     * Reprocessa um treino já registrado — o gesto "este treino mudou ou saiu": laps adicionadas,
     * data alterada, ou o treino foi cancelado.
     *
     * <p><b>Ordem garantida:</b> recarrega o treino por id → recalcula {@code tssCalculado} apenas
     * se o treino conta na carga (não {@code CANCELADO}) e não é de dispositivo (D3.1) → recalcula
     * a carga de {@code min(dataAnterior, treino.getDataTreino())} (ou só de
     * {@code treino.getDataTreino()} quando {@code dataAnterior} é nulo) até hoje. Nunca publica
     * evento — só {@link #registrar} publica.</p>
     *
     * Idempotent: YES — reprocessar o mesmo treino sem mudança produz o mesmo resultado.
     * Side Effects: Database update do treino (quando recalcula tssCalculado) e das métricas
     * diárias afetadas.
     * Tenant-aware: NO
     *
     * @param treinoRealizadoId id do treino a reprocessar
     * @param dataAnterior      a data do treino antes da mudança que motivou o reprocessamento,
     *                          lida pelo chamador antes de mutar/salvar a entidade; {@code null}
     *                          quando a data não mudou (laps, cancelamento, reconciliação)
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o treino não existe
     */
    void reprocessar(UUID treinoRealizadoId, @Nullable LocalDate dataAnterior);
}
