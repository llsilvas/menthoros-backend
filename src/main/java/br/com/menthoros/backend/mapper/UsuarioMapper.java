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
     * @param usuario entidade do usuário autenticado (obrigatório)
     * @param atleta  atleta vinculado quando a role for ATLETA; null quando não houver vínculo
     * @param lgpd    estado de consentimento já resolvido pelo serviço (obrigatório)
     * @return DTO de identidade
     * @throws IllegalArgumentException se usuario ou lgpd forem null
     */
    /**
     * Rota que serve a logo — a mesma de {@code GET /assessorias/me}. Dois endpoints descrevendo o
     * mesmo recurso com URLs diferentes é divergência esperando para acontecer.
     */
    public static final String LOGO_PATH = "/api/v1/assessorias/me/logo";

    /**
     * @param temLogo se a assessoria tem logo cadastrada. Vem resolvido pela service: a logo é um
     *                BLOB em {@code tb_assessoria_logo}, e consultar isso aqui colocaria acesso a
     *                repositório dentro de um mapper — que então não se testa isolado. O campo
     *                legado {@code Assessoria.logoUrl} <b>não</b> serve: está {@code NULL} desde a
     *                migração para BLOB.
     */
    public UsuarioMeOutputDto toMeOutputDto(Usuario usuario, Atleta atleta, LgpdConsentStatus lgpd,
                                            boolean temLogo) {
        if (usuario == null) {
            throw new IllegalArgumentException("Usuario cannot be null");
        }
        if (lgpd == null) {
            throw new IllegalArgumentException("LgpdConsentStatus cannot be null");
        }
        return new UsuarioMeOutputDto(
                usuario.getId(),
                usuario.getNomeCompleto(),
                usuario.getEmail(),
                usuario.getAvatarUrl(),
                usuario.getRole(),
                toAssessoria(usuario.getAssessoria(), temLogo),
                atleta != null ? atleta.getId() : null,
                lgpd.granted(),
                lgpd.currentPolicyVersion(),
                lgpd.currentTermsVersion(),
                lgpd.consentedAt(),
                lgpd.acceptedPolicyVersion(),
                lgpd.acceptedTermsVersion(),
                usuario.isOnboardingConcluido()
        );
    }

    private UsuarioMeOutputDto.Assessoria toAssessoria(Assessoria assessoria, boolean temLogo) {
        if (assessoria == null) {
            return null;
        }
        return new UsuarioMeOutputDto.Assessoria(
                assessoria.getId(),
                assessoria.getNome(),
                assessoria.getDominio(),
                temLogo,
                temLogo ? LOGO_PATH : null,
                assessoria.getVersion()
        );
    }
}
