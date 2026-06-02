package br.com.menthoros.backend.skills.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orquestrador de skills de domínio.
 *
 * <p>Executa uma lista de skills em ordem, captura exceptions por skill sem propagar
 * para as demais, e retorna a lista consolidada de {@link SkillResult}.</p>
 *
 * <p>Design intencional:</p>
 * <ul>
 *   <li>Falha isolada: uma skill com exception não bloqueia as demais.</li>
 *   <li>Ordem preservada: resultados retornados na mesma ordem das skills de entrada.</li>
 *   <li>Sem estado: todas as chamadas são stateless e thread-safe.</li>
 * </ul>
 */
@Slf4j
@Service
public class SkillOrchestratorService {

    /**
     * Executa todas as skills fornecidas com o contexto dado.
     *
     * <p><b>Idempotente:</b> SIM — depende das skills individuais;
     * o orquestrador em si não mantém estado.</p>
     * <p><b>Side Effects:</b> NONE no orquestrador; skills individuais podem ter side effects.</p>
     * <p><b>Tenant-aware:</b> SIM — tenantId está no {@link SkillContext}.</p>
     *
     * @param skills  lista de skills a executar, na ordem desejada
     * @param context contexto compartilhado de execução (atletaId, tenantId, dataReferencia)
     * @return lista de resultados na mesma ordem das skills de entrada;
     *         skills que lançaram exception são omitidas da lista
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<SkillResult<?>> execute(List<DomainSkill<?, ?>> skills, SkillContext context) {
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }

        log.info("SkillOrchestratorService iniciado: atletaId={}, skills={}",
                context.atletaId(), skills.stream().map(DomainSkill::skillKey).toList());

        List<SkillResult<?>> results = new ArrayList<>(skills.size());

        for (DomainSkill skill : skills) {
            try {
                long start = System.currentTimeMillis();
                SkillResult<?> result = skill.execute(null, context);
                long elapsed = System.currentTimeMillis() - start;

                log.debug("Skill executada: key={}, severity={}, confidence={}, latency={}ms",
                        result.skillKey(), result.severity(), result.confidence(), elapsed);

                results.add(result);
            } catch (Exception ex) {
                log.error("Skill falhou e foi ignorada: key={}, error={}",
                        skill.skillKey(), ex.getMessage(), ex);
                // Falha isolada — não propagar; demais skills continuam
            }
        }

        log.info("SkillOrchestratorService concluído: atletaId={}, resultados={}/{}",
                context.atletaId(), results.size(), skills.size());

        return List.copyOf(results);
    }
}
