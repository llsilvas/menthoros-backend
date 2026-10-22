package br.com.menthoros.backend.enums;

/**
 * Etapa do lead da waitlist no funil — derivada em {@code Waitlist.getStatus()} a partir de
 * timestamps, nunca persistida como coluna própria (add-waitlist-status-lifecycle, design D2).
 *
 * <p>Não confundir com {@code WaitlistSegment} (expand-waitlist-access-contract): {@code status}
 * responde "em que etapa do funil o lead está"; {@code segment} responde "que tipo de lead é
 * este". Eixos ortogonais.
 */
public enum WaitlistStatus {
    NEW,
    INVITED,
    ACTIVE,
    DISCARDED
}
