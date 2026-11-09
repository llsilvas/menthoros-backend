package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.TrajetoShareConsentOutputDto;

/** Consentimento do atleta autenticado para o trajeto GPS aparecer nos cards de compartilhamento. */
public interface TrajetoShareConsentService {

    /** Idempotent: YES · Side Effects: NONE · Tenant-aware: YES. */
    TrajetoShareConsentOutputDto estadoAtual();

    /**
     * Registra concessão para a versão que o aviso apresentou.
     *
     * Idempotent: YES (já concedido na versão vigente não grava de novo) · Side Effects: insert
     * append-only · Tenant-aware: YES.
     *
     * @throws br.com.menthoros.backend.exception.ConsentVersionStaleException se a versão não é a vigente
     */
    TrajetoShareConsentOutputDto conceder(String policyVersionApresentada);

    /** Idempotent: YES (já revogado não grava de novo) · Side Effects: insert append-only · Tenant-aware: YES. */
    TrajetoShareConsentOutputDto revogar();
}
