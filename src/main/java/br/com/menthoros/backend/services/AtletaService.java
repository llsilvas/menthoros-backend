package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.AtletaInputDto;
import br.com.menthoros.backend.dto.output.AtletaOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;

import java.util.List;
import java.util.UUID;

public interface AtletaService {

    Atleta createAtleta(AtletaInputDto atletaInputDto);

    AtletaOutputDto updateAtleta(UUID id, AtletaInputDto atletaInputDto);

    void deleteAtleta(UUID id);

    AtletaOutputDto getAtletaById(UUID id);

    List<AtletaOutputDto> getAllAtletas(String nome, NivelExperiencia nivelExperiencia, Boolean temLesao);

    void recalcularMetricasAtleta(UUID id);

    /**
     * Gera ou reenvia o convite de acesso para o atleta via Keycloak Organization.
     *
     * Idempotent: YES — reenviar o convite não duplica o atleta nem cria estado divergente.
     * Side Effects: External API (Keycloak — envio de convite).
     * Tenant-aware: YES — usa TenantContext.getRequiredTenantId().
     *
     * @param atletaId UUID do atleta a ser convidado
     * @throws DomainNotFoundException se o atleta não pertencer ao tenant atual
     * @throws DomainRuleViolationException se o atleta não possuir email
     */
    void gerarConvite(UUID atletaId);
}
