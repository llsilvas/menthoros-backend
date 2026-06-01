package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.RaceProjectionRequestDto;
import br.com.menthoros.backend.dto.output.AthleteProjectionViewDto;
import br.com.menthoros.backend.dto.output.RaceProjectionSnapshotOutputDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceProjectionService {

    /**
     * Gera uma nova projeção de tempo de prova para um atleta e persiste como snapshot.
     *
     * Idempotent: NO — cada chamada gera um novo snapshot.
     * Side Effects: External API call (LLM), Database insert.
     * Tenant-aware: YES
     */
    RaceProjectionSnapshotOutputDto generate(UUID atletaId, UUID tenantId, UUID coachId,
                                              RaceProjectionRequestDto request);

    /**
     * Retorna histórico de snapshots de um atleta para uma prova, do mais recente ao mais antigo.
     *
     * Idempotent: YES
     * Side Effects: NONE
     * Tenant-aware: YES
     */
    List<RaceProjectionSnapshotOutputDto> getHistory(UUID atletaId, UUID provaId, UUID tenantId);

    /**
     * Retorna o snapshot oficial atual de um atleta para uma prova.
     *
     * Idempotent: YES
     * Side Effects: NONE
     * Tenant-aware: YES
     */
    Optional<RaceProjectionSnapshotOutputDto> getOfficial(UUID atletaId, UUID provaId, UUID tenantId);

    /**
     * Marca um snapshot como oficial, desmarcando o anterior se existir.
     *
     * Idempotent: YES (marcar o mesmo snapshot duas vezes é inofensivo)
     * Side Effects: Database update (troca de oficial)
     * Tenant-aware: YES
     */
    RaceProjectionSnapshotOutputDto markAsOfficial(UUID snapshotId, UUID atletaId, UUID provaId,
                                                    UUID tenantId, UUID coachId);

    /**
     * Retorna a visão simplificada da projeção oficial para o atleta.
     * Somente retorna se houver projeção oficial E revisada pelo coach.
     *
     * Idempotent: YES
     * Side Effects: NONE
     * Tenant-aware: YES
     */
    Optional<AthleteProjectionViewDto> getAthleteView(UUID atletaId, UUID provaId, UUID tenantId);
}
