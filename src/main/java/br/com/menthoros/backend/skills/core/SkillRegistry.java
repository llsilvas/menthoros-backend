package br.com.menthoros.backend.skills.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Registry de descoberta automática de skills de domínio.
 *
 * <p>Injeta automaticamente todos os beans que implementam {@link DomainSkill}
 * via lista Spring. Expõe acesso por chave e listagem de todas as skills disponíveis.</p>
 *
 * <p>Para registrar uma nova skill, basta anotá-la com {@code @Component} (ou
 * {@code @Service}) e implementar {@link DomainSkill}. O registry a detecta automaticamente.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillRegistry {

    @SuppressWarnings("rawtypes")
    private final List<DomainSkill> skills;

    /**
     * Busca uma skill pelo seu {@code skillKey}.
     *
     * <p><b>Idempotente:</b> SIM — consulta pura, sem mutação de estado.</p>
     * <p><b>Side Effects:</b> NONE.</p>
     * <p><b>Tenant-aware:</b> NÃO — o registry é global.</p>
     *
     * @param key chave única da skill (ex: "recovery-load-v1")
     * @return Optional com a skill encontrada, ou Optional.empty() se não encontrada
     */
    @SuppressWarnings("rawtypes")
    public Optional<DomainSkill> findByKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return skills.stream()
                .filter(s -> key.equals(s.skillKey()))
                .findFirst();
    }

    /**
     * Lista todas as skills registradas no contexto Spring.
     *
     * <p><b>Idempotente:</b> SIM — lista imutável dos beans detectados.</p>
     * <p><b>Side Effects:</b> NONE.</p>
     * <p><b>Tenant-aware:</b> NÃO — o registry é global.</p>
     *
     * @return lista imutável de todas as {@link DomainSkill} disponíveis
     */
    @SuppressWarnings("rawtypes")
    public List<DomainSkill> listAll() {
        return List.copyOf(skills);
    }
}
