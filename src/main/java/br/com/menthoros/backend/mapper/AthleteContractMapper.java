package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.output.AthleteContractOutputDto;
import br.com.menthoros.backend.dto.output.AthleteInvoiceOutputDto;
import br.com.menthoros.backend.entity.AthleteContract;
import br.com.menthoros.backend.entity.AthleteInvoice;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** Conversão manual (records): nulo é erro de programação, nunca retorno silencioso. */
@Component
public class AthleteContractMapper {

    public AthleteContractOutputDto toOutputDto(AthleteContract contract, List<AthleteInvoice> invoices, LocalDate today) {
        if (contract == null) {
            throw new IllegalArgumentException("AthleteContract cannot be null");
        }
        if (invoices == null) {
            throw new IllegalArgumentException("invoices cannot be null");
        }
        return new AthleteContractOutputDto(
                contract.getId(),
                contract.getAthleteId(),
                contract.getPeriodicity(),
                contract.getAmount(),
                contract.getDueDay(),
                contract.getStartDate(),
                contract.getEndedAt(),
                contract.isActive(),
                contract.isAthleteNoticeEnabled(),
                invoices.stream().map(i -> toOutputDto(i, today)).toList());
    }

    public AthleteInvoiceOutputDto toOutputDto(AthleteInvoice invoice, LocalDate today) {
        if (invoice == null) {
            throw new IllegalArgumentException("AthleteInvoice cannot be null");
        }
        if (today == null) {
            throw new IllegalArgumentException("today cannot be null");
        }
        return new AthleteInvoiceOutputDto(
                invoice.getId(),
                invoice.getContractId(),
                invoice.getDueDate(),
                invoice.getAmount(),
                invoice.getStatus(),
                invoice.getPaidAt(),
                invoice.getPaidAmount(),
                invoice.isOverdue(today));
    }
}
