package br.com.menthoros.backend.skills.core;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.SkillExecution;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.SkillExecutionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Orquestrador de skills de domínio.
 *
 * <p>Executa uma lista de skills em ordem, captura exceptions por skill sem propagar
 * para as demais, retorna a lista consolidada de {@link SkillResult} e persiste
 * cada resultado executado com sucesso como {@link SkillExecution}.</p>
 *
 * <p>Design intencional:</p>
 * <ul>
 *   <li>Falha isolada: uma skill com exception não bloqueia as demais.</li>
 *   <li>Ordem preservada: resultados retornados na mesma ordem das skills de entrada.</li>
 *   <li>Sem estado: todas as chamadas são stateless e thread-safe.</li>
 *   <li>Persistência best-effort: falha na serialização ou persistência é logada como WARN
 *       e não bloqueia o retorno dos resultados.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillOrchestratorService {

    private final SkillExecutionRepository skillExecutionRepository;
    private final AtletaRepository atletaRepository;
    private final ObjectMapper objectMapper;

    /**
     * Executa todas as skills fornecidas com o contexto dado e persiste cada resultado.
     *
     * <p><b>Idempotente:</b> NÃO — cada chamada cria novos registros em {@code tb_skill_execution}.</p>
     * <p><b>Side Effects:</b> Database insert — um {@link SkillExecution} por skill bem-sucedida.</p>
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

        Atleta atleta = resolveAtleta(context);

        List<SkillResult<?>> results = new ArrayList<>(skills.size());

        for (DomainSkill skill : skills) {
            try {
                long start = System.currentTimeMillis();
                SkillResult<?> result = skill.execute(null, context);
                long elapsed = System.currentTimeMillis() - start;

                log.debug("Skill executada: key={}, severity={}, confidence={}, latency={}ms",
                        result.skillKey(), result.severity(), result.confidence(), elapsed);

                results.add(result);

                persistExecution(result, context, atleta);

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

    // ─── helpers privados ────────────────────────────────────────────────────────

    /**
     * Tenta resolver o atleta pelo ID do contexto.
     * Retorna null se não encontrado — a persistência usará null para atleta_id.
     */
    private Atleta resolveAtleta(SkillContext context) {
        if (context.atletaId() == null) {
            return null;
        }
        try {
            return atletaRepository.findById(context.atletaId()).orElse(null);
        } catch (Exception ex) {
            log.warn("Não foi possível resolver atleta para persistência de SkillExecution: atletaId={}, error={}",
                    context.atletaId(), ex.getMessage());
            return null;
        }
    }

    /**
     * Persiste o resultado de uma skill como {@link SkillExecution}.
     * Falhas de serialização ou persistência são logadas como WARN e não propagadas.
     */
    private void persistExecution(SkillResult<?> result, SkillContext context, Atleta atleta) {
        try {
            String payloadJson = serializeToJson(result.payload());
            String evidenceJson = serializeToJson(result.evidence());
            String recommendationsJson = serializeToJson(result.recommendations());

            SkillExecution execution = SkillExecution.builder()
                    .skillKey(result.skillKey())
                    .skillVersion(result.skillVersion())
                    .severity(result.severity() != null ? result.severity().name() : null)
                    .confidence(result.confidence() != null ? result.confidence().name() : null)
                    .payloadJson(payloadJson)
                    .evidenceJson(evidenceJson)
                    .recommendationsJson(recommendationsJson)
                    .tenantId(context.tenantId())
                    .atleta(atleta)
                    .executedAt(Instant.now())
                    .dataReferencia(context.dataReferencia())
                    .build();

            skillExecutionRepository.save(execution);

            log.debug("SkillExecution persistida: skillKey={}, atletaId={}, tenantId={}",
                    result.skillKey(), context.atletaId(), context.tenantId());

        } catch (Exception ex) {
            log.warn("Falha ao persistir SkillExecution (continuando): skillKey={}, atletaId={}, error={}",
                    result.skillKey(), context.atletaId(), ex.getMessage());
            // Best-effort: não bloquear o fluxo principal
        }
    }

    /**
     * Serializa um objeto para JSON.
     * Retorna null se o objeto for null.
     *
     * @throws JsonProcessingException se a serialização falhar
     */
    private String serializeToJson(Object value) throws JsonProcessingException {
        if (value == null) {
            return null;
        }
        return objectMapper.writeValueAsString(value);
    }
}
