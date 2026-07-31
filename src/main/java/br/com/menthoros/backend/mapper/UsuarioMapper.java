package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.output.UsuarioMeOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Usuario;
import org.springframework.stereotype.Component;

/**
 * Conversão Usuario → UsuarioMeOutputDto para o endpoint GET /api/v1/users/me.
 */
@Component
public class UsuarioMapper {

    /**
     * Converte o Usuario autenticado no DTO de identidade, preenchendo atletaId
     * apenas quando o Atleta vinculado for informado.
     *
     * Idempotent: YES — Read-only, sem side effects.
     * Side Effects: NONE
     * Tenant-aware: NO — isolamento garantido pela camada de serviço que resolve as entidades.
     *
     * @param usuario              entidade do usuário autenticado (obrigatório)
     * @param atleta               atleta vinculado quando a role for ATLETA; null quando não houver vínculo
     * @param lgpdConsentGranted   se já existe aceite das versões vigentes (derivado pelo serviço)
     * @param policyVersionVigente data de vigência da Política em vigor
     * @param termsVersionVigente  data de vigência dos Termos em vigor
     * @return DTO de identidade
     * @throws IllegalArgumentException se usuario for null
     */
    public UsuarioMeOutputDto toMeOutputDto(Usuario usuario,
                                            Atleta atleta,
                                            boolean lgpdConsentGranted,
                                            String policyVersionVigente,
                                            String termsVersionVigente) {
        if (usuario == null) {
            throw new IllegalArgumentException("Usuario cannot be null");
        }
        return new UsuarioMeOutputDto(
                usuario.getId(),
                usuario.getNomeCompleto(),
                usuario.getEmail(),
                usuario.getRole(),
                toAssessoria(usuario.getAssessoria()),
                atleta != null ? atleta.getId() : null,
                lgpdConsentGranted,
                policyVersionVigente,
                termsVersionVigente
        );
    }

    private UsuarioMeOutputDto.Assessoria toAssessoria(Assessoria assessoria) {
        if (assessoria == null) {
            return null;
        }
        return new UsuarioMeOutputDto.Assessoria(
                assessoria.getId(),
                assessoria.getNome(),
                assessoria.getDominio()
        );
    }
}
