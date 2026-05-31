package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.skills.race.AthleteProfile;
import org.springframework.stereotype.Component;

/**
 * Única fonte de verdade para a conversão Atleta → AthleteProfile.
 * Isola o pacote skills.race de dependências JPA — ver Skills Architecture Standards no CLAUDE.md.
 */
@Component
public class AthleteProfileMapper {

    /**
     * Converte Atleta para AthleteProfile com os campos mínimos necessários pela RaceProjectionSkill.
     *
     * Idempotent: YES — Read-only, sem side effects.
     * Side Effects: NONE
     * Tenant-aware: NO — AthleteProfile não carrega tenant; isolamento garantido pela camada de serviço.
     *
     * @param atleta entidade JPA do atleta
     * @return AthleteProfile com campos fisiológicos mapeados
     * @throws IllegalArgumentException se atleta for null
     */
    public AthleteProfile from(Atleta atleta) {
        if (atleta == null) {
            throw new IllegalArgumentException("Atleta cannot be null");
        }
        return new AthleteProfile(
                atleta.getId(),
                atleta.getNome(),
                atleta.getSobrenome(),
                atleta.getFcMaxima(),
                atleta.getFcLimiar(),
                atleta.getVo2maxEstimado(),
                atleta.getNivelExperiencia()
        );
    }
}
