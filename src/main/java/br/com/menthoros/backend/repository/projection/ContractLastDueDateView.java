package br.com.menthoros.backend.repository.projection;

import java.time.LocalDate;
import java.util.UUID;

/** Último vencimento gerado por contrato — base do próximo vencimento calculado. */
public interface ContractLastDueDateView {
    UUID getContractId();

    LocalDate getLastDueDate();
}
