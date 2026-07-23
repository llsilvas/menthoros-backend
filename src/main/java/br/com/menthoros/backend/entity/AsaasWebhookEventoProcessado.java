package br.com.menthoros.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Controle de idempotência do webhook do Asaas ({@code tb_asaas_webhook_evento_processado}, V69).
 * Um evento já registrado (mesmo {@code eventoId}) não é reprocessado (CA10) — o Asaas entrega
 * at-least-once (até 5 reenvios).
 */
@Entity
@Table(name = "tb_asaas_webhook_evento_processado")
@Getter
@Setter
@NoArgsConstructor
public class AsaasWebhookEventoProcessado {

    @Id
    @Column(name = "evento_id", length = 100)
    private String eventoId;

    @Column(name = "tipo_evento", length = 50)
    private String tipoEvento;

    @Column(name = "processado_em", nullable = false)
    private Instant processadoEm;

    public AsaasWebhookEventoProcessado(String eventoId, String tipoEvento) {
        this.eventoId = eventoId;
        this.tipoEvento = tipoEvento;
    }

    @PrePersist
    private void prePersist() {
        if (processadoEm == null) {
            processadoEm = Instant.now();
        }
    }
}
