package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Persiste um {@link TreinoRealizado} de forma idempotente: se o mesmo (externalId, atletaId) já
 * foi inserido por uma requisição anterior (já commitada), retorna o registro existente em vez de
 * duplicar.
 *
 * <p>Usado pelos imports externos (Strava, .fit) que compartilham exatamente esse padrão de
 * dedup — mantido em um único lugar para não divergir silenciosamente entre eles.
 *
 * <p><b>Duas constraints únicas coexistem hoje sobre {@code tb_treino_realizado}:</b>
 * {@code uk_treino_realizado_external_id_atleta} (external_id, atleta_id — V23), exatamente as
 * colunas que {@code findByExternalIdAndAtletaId} consulta, e
 * {@code uk_treino_realizado_tenant_fonte_external} (tenant_id, fonte_dados, external_id — V29).
 * A busca de fallback abaixo só cobre a primeira: se for a segunda que disparar (mesmo
 * external_id, mesma fonte, mesmo tenant, mas ATLETA DIFERENTE do desta chamada — dado corrompido
 * ou fonte externa reaproveitando um id para outra pessoa), {@code findByExternalIdAndAtletaId}
 * genuinamente não encontra nada para {@code atletaId} e a exceção original propaga — o
 * comportamento correto, já que não é uma duplicata legítima para ESTE atleta.</p>
 *
 * <p><b>O que este método garante e o que não garante:</b> a checagem prévia em
 * {@code IngestaoTreinoRealizadoServiceImpl.registrar} cobre o caso comum — duplicata cujo insert
 * original já commitou antes desta chamada começar. Para esse caso, {@code saveIdempotent} nem
 * chega a tentar o insert.
 *
 * <p>Sob uma corrida <b>verdadeiramente concorrente</b> — duas transações que passam pela
 * checagem prévia antes de qualquer uma commitar — o insert desta chamada roda dentro da
 * transação ambiente do chamador (todos os chamadores atuais são {@code @Transactional}, D6). No
 * Postgres, uma violação de constraint marca a transação inteira como {@code aborted}: a query de
 * fallback abaixo também falharia com {@code 25P02} se tentada na mesma transação. Uma correção
 * por savepoint ({@code Propagation.NESTED}) foi tentada e revertida — o {@code JpaDialect} deste
 * projeto não suporta savepoints reais, e forçar {@code nestedTransactionAllowed} quebrou
 * suítes de integração já existentes com {@code NestedTransactionNotSupportedException}.
 *
 * <p><b>Decisão (achado de QA, 2026-08-22):</b> sob essa corrida rara, a exceção propaga sem
 * tentar recuperação no meio da transação — a transação ambiente do chamador sofre rollback
 * completo e limpo (comportamento padrão do Spring/Postgres, sem corrupção de dado). O chamador
 * (scheduler ou requisição HTTP) trata isso como uma falha transitória comum e tenta de novo mais
 * tarde; na nova tentativa, a checagem prévia em {@code registrar} já vê a linha commitada pelo
 * vencedor da corrida anterior e trata como duplicata graciosamente. Verificado com
 * {@link TreinoDedupHelperConcorrenciaIT}: duas transações reais e concorrentes contra o mesmo
 * (externalId, atletaId) — uma insere, a outra falha e sofre rollback limpo (nunca 500 vazando
 * estado inconsistente nem duplicata na tabela).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TreinoDedupHelper {

    private final TreinoRealizadoRepository treinoRealizadoRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Tenta persistir {@code treino}; se o (externalId, atletaId) já foi commitado por outra
     * requisição antes desta chamada, retorna o registro existente em vez de duplicar. Sob
     * corrida verdadeiramente concorrente (ver javadoc da classe), propaga a exceção — o chamador
     * sofre rollback limpo e deve tratar como falha transitória retryable.
     *
     * <p><b>Idempotent:</b> YES — reenviar o mesmo (externalId, atletaId) nunca duplica a linha;
     * sob corrida verdadeiramente concorrente, uma das duas chamadas propaga exceção em vez de
     * retornar graciosamente (residual documentado, ver javadoc da classe).
     * <p><b>Side Effects:</b> Database insert (quando não há conflito) ou apenas leitura (quando
     * o registro já existe, commitado antes desta chamada).
     * <p><b>Tenant-aware:</b> NO diretamente — depende do chamador já ter validado que
     * {@code atletaId} pertence ao tenant atual antes de chamar este método.
     *
     * @return um {@link SaveResult} que expõe explicitamente se ESTA chamada inseriu o registro
     *         (via {@link SaveResult#inserted()}) — chamadores que disparam side effects (eventos,
     *         recálculo de métricas) só quando um registro NOVO foi criado devem checar essa flag
     *         em vez de inferir a partir de identidade de objeto/referência.
     */
    public SaveResult saveIdempotent(TreinoRealizado treino, String externalId, UUID atletaId) {
        try {
            TreinoRealizado salvo = treinoRealizadoRepository.save(treino);
            // Flush explícito: dentro de um chamador @Transactional (todos os atuais são), o
            // INSERT fica pendente até a próxima query no mesmo EntityManager — sem forçar aqui,
            // a violação de constraint só apareceria depois, fora deste try/catch, como exceção
            // não tratada em vez de propagar corretamente daqui.
            entityManager.flush();
            return new SaveResult(salvo, true);
        } catch (DataIntegrityViolationException | ConstraintViolationException e) {
            // ConstraintViolationException (Hibernate) chega crua do flush() direto no
            // EntityManager — sem passar por um método de repositório Spring, não há tradução de
            // exceção; DataIntegrityViolationException continua coberta por retrocompatibilidade
            // (é o que os testes existentes simulam, e o que save() por si só poderia lançar).
            //
            // A busca abaixo só encontra o vencedor quando o commit dele já aconteceu ANTES desta
            // exceção (ex.: corrida quase-simultânea onde o outro completou primeiro dentro da
            // janela de rede). Sob corrida genuinamente concorrente na mesma janela de tempo, a
            // transação Postgres já está "aborted" neste ponto e esta query também falha — a
            // exceção original é então propagada (catch abaixo relança), causando rollback limpo
            // da transação do chamador. Ver javadoc da classe.
            log.warn(
                "Deduplication: constraint violation on (externalId={}, atletaId={}), " +
                "retrying fetch. This is normal under concurrent load.",
                externalId, atletaId
            );

            try {
                var existing = treinoRealizadoRepository.findByExternalIdAndAtletaId(externalId, atletaId);

                if (existing.isPresent()) {
                    log.info(
                        "Deduplication idempotent: returning existing TreinoRealizado {} " +
                        "for (externalId={}, atletaId={})",
                        existing.get().getId(), externalId, atletaId
                    );
                    return new SaveResult(existing.get(), false);
                }

                log.error(
                    "CRITICAL: Deduplication failed - constraint violation but record not found. " +
                    "externalId={}, atletaId={}", externalId, atletaId
                );
            } catch (RuntimeException fallbackException) {
                log.warn(
                    "Deduplication: fallback fetch falhou (transação provavelmente já 'aborted' — "
                    + "corrida verdadeiramente concorrente). Propagando a exceção original para "
                    + "rollback limpo. externalId={}, atletaId={}",
                    externalId, atletaId
                );
            }
            throw e;
        }
    }

    /**
     * @param treino   o treino persistido (novo, se {@code inserted}, ou já existente, se não)
     * @param inserted {@code true} quando ESTA chamada inseriu o registro; {@code false} quando
     *                 uma requisição concorrente já havia inserido e este resultado é o vencedor
     *                 dessa corrida buscado do banco
     */
    public record SaveResult(TreinoRealizado treino, boolean inserted) {}
}
