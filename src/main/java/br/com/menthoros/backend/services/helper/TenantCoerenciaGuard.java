package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.exception.AccessDeniedException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Recusa a escrita quando o usuário autenticado não pertence ao tenant que o JWT resolveu.
 *
 * <p><b>Por que isso é necessário se o banco já garante 1:1.</b> {@code Usuario.assessoria} é
 * {@code @ManyToOne(optional = false)} e {@code keycloak_id} é único, então um {@code sub} mapeia
 * para exatamente um tenant — no <i>banco</i>. O Keycloak não tem essa restrição: adicionar o mesmo
 * usuário a duas Organizations é um clique. Quando isso acontece:
 *
 * <ul>
 *   <li>{@code JwtTenantFilter} itera um {@code Map} de organizations e devolve a primeira com
 *       {@code tenant_id} válido — ordem de JSON desserializado, portanto arbitrária;</li>
 *   <li>{@code UsuarioSyncServiceImpl} busca por {@code keycloakId} <b>sem</b> filtrar tenant e
 *       nunca reatribui {@code assessoria}, então o {@code TenantContext} pode apontar para B
 *       enquanto a linha do usuário continua em A, em silêncio.</li>
 * </ul>
 *
 * <p>Enquanto não havia escrita de identidade de assessoria, isso era latente. Esta change criou a
 * primeira — daí o gate. Ele <b>não</b> conserta a resolução ambígua de tenant: impede apenas que
 * ela produza escrita no tenant errado. A correção da causa é maior que esta change.
 *
 * <p>Escopo honesto: protege a assessoria, não a linha do usuário. O sync do
 * {@code JwtTenantFilter} já gravou email/nome/role/último acesso antes de qualquer serviço rodar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantCoerenciaGuard {

    private final UsuarioRepository usuarioRepository;
    private final AuthenticatedPrincipalResolver principalResolver;

    /**
     * @return o tenant corrente, já confirmado como o do usuário autenticado
     * @throws AccessDeniedException se o usuário do {@code sub} não pertencer a este tenant
     */
    public UUID exigirCoerencia() {
        UUID tenantId = TenantContext.getRequiredTenantId();
        String sub = principalResolver.getCurrentSubject();

        // A consulta é o próprio gate: filtrar por sub E tenant devolve vazio exatamente quando os
        // dois divergem.
        usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId)
                .orElseThrow(() -> {
                    // sub apenas no log — não expor identificador do Keycloak na resposta HTTP
                    log.warn("Escrita recusada: usuário não pertence ao tenant resolvido. "
                            + "sub={}, tenantId={}", sub, tenantId);
                    return new AccessDeniedException(
                            "Usuário não pertence à assessoria da sessão atual");
                });

        return tenantId;
    }
}
