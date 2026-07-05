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

    public TreinoRealizado saveIdempotent(TreinoRealizado treino, String externalId, UUID atletaId) {
        try {
            return treinoRealizadoRepository.save(treino);
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
                return existing.get();
            }

            log.error(
                "CRITICAL: Deduplication failed - constraint violation but record not found. " +
                "externalId={}, atletaId={}", externalId, atletaId
            );
            throw e;
        }
    }
}
