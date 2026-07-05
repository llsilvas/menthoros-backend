package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Persiste um {@link TreinoRealizado} de forma idempotente sob concorrência: se dois imports do
 * mesmo (externalId, atletaId) colidirem na constraint única entre o pré-check e o insert,
 * retorna o registro que "venceu" a corrida em vez de propagar a exceção.
 *
 * <p>Usado pelos imports externos (Strava, .fit) que compartilham exatamente esse padrão de
 * dedup — mantido em um único lugar para não divergir silenciosamente entre eles.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TreinoDedupHelper {

    private final TreinoRealizadoRepository treinoRealizadoRepository;

    /**
     * Tenta persistir {@code treino}; sob corrida de concorrência na constraint única, retorna o
     * registro que já foi inserido por outra requisição em vez de propagar o erro.
     *
     * <p><b>Idempotent:</b> YES — reenviar o mesmo (externalId, atletaId) nunca duplica a linha.
     * <p><b>Side Effects:</b> Database insert (quando não há conflito) ou apenas leitura (quando
     * outra requisição já venceu a corrida).
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
            return new SaveResult(salvo, true);
        } catch (DataIntegrityViolationException e) {
            log.warn(
                "Deduplication: constraint violation on (externalId={}, atletaId={}), " +
                "retrying fetch. This is normal under concurrent load.",
                externalId, atletaId
            );

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
