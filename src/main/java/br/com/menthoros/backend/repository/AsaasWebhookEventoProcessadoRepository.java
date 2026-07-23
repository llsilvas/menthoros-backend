package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.AsaasWebhookEventoProcessado;
import org.springframework.data.repository.CrudRepository;

/**
 * Controle de idempotência do webhook do Asaas (CA10). PK = {@code eventoId}.
 */
public interface AsaasWebhookEventoProcessadoRepository
        extends CrudRepository<AsaasWebhookEventoProcessado, String> {
}
