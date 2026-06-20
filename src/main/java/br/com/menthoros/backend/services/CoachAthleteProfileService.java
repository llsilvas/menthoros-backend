package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.AtletaPerfilCoachOutputDto;

import java.util.UUID;

/**
 * Agrega dados de múltiplas fontes em um único perfil do atleta para o coach.
 * Read-only e tenant-aware (resolve tenant via TenantContext).
 */
public interface CoachAthleteProfileService {

    /**
     * Retorna o perfil completo de um atleta: PMC 90d, aderência 8 semanas, plano vigente,
     * sinais recentes, sugestões recentes e recordes pessoais.
     *
     * <p>Sub-serviços são invocados em sequência. Falhas parciais são registradas em
     * {@link AtletaPerfilCoachOutputDto#avisos()} e os blocos restantes continuam carregando.
     *
     * <p><b>Idempotent:</b> YES — leitura pura.
     * <b>Side Effects:</b> NONE.
     * <b>Tenant-aware:</b> YES — valida que {@code atletaId} pertence ao tenant antes de qualquer sub-chamada.
     *
     * @param atletaId ID do atleta
     * @return perfil consolidado
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se atleta não existe no tenant
     */
    AtletaPerfilCoachOutputDto buscarPerfil(UUID atletaId);
}
