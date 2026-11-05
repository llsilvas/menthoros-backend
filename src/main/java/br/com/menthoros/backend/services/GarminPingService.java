package br.com.menthoros.backend.services;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** Recepção dos pings {@code activityFiles} do Garmin (garmin-activity-ingestion, D2). */
public interface GarminPingService {

    /**
     * Grava cada item como ping {@code PENDING} ou {@code DISCARDED} (com motivo), sem nenhuma
     * chamada ao Garmin. {@code summaryId} repetido é ignorado; {@code userId} sem conexão
     * registrada não grava; item sem campo obrigatório é pulado.
     *
     * <p>Idempotent: YES — o mesmo lote reenviado converge pelo UNIQUE de {@code summary_id}.
     * <p>Side Effects: Database insert por item, cada um em transação própria.
     * <p>Tenant-aware: NO — o ping não tem tenant; vem da conexão achada pelo {@code userId}.
     *
     * @throws br.com.menthoros.backend.exception.GarminPingRegistroException se uma gravação falhar
     *         por outro motivo que não o dedup — sem causa, para o Garmin reenviar (500)
     */
    void registrar(List<? extends JsonNode> itens);
}
