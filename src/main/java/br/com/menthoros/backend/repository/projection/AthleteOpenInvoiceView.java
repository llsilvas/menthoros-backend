package br.com.menthoros.backend.repository.projection;

import java.time.LocalDate;
import java.util.UUID;

/** Projeção de mensalidade em aberto por atleta, para o status de cobrança em lote. */
public interface AthleteOpenInvoiceView {
    UUID getAthleteId();

    LocalDate getDueDate();
}
