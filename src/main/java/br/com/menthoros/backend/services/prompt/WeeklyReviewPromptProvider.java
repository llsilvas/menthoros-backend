package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.entity.RevisaoSemanal;
import br.com.menthoros.backend.repository.RevisaoSemanalRepository;
import br.com.menthoros.backend.services.helper.RevisaoSemanalCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolve qual {@link RevisaoSemanal} pode ser consumida como insumo do plano que está sendo gerado.
 *
 * <p>Ponto único onde a flag de injeção e a janela de validade (D11) são aplicadas — tanto o prompt
 * quanto a gravação do vínculo no plano passam por aqui, então os dois nunca divergem sobre "houve
 * consumo". Vazio ⇒ o plano registra {@code NOT_CONSUMED} e nada entra no prompt.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WeeklyReviewPromptProvider {

    private final RevisaoSemanalRepository revisaoSemanalRepository;

    @Value("${menthoros.weekly-review.injection.enabled:true}")
    private boolean injecaoHabilitada;

    /**
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES — query escopada por tenant.
     *
     * @param semanaInicioPlano início da semana do plano sendo gerado (base da janela D11)
     */
    public Optional<RevisaoSemanal> resolverParaGeracao(UUID atletaId, UUID tenantId,
                                                        LocalDate semanaInicioPlano) {
        if (!injecaoHabilitada || atletaId == null || tenantId == null) {
            return Optional.empty();
        }
        List<RevisaoSemanal> recentes = revisaoSemanalRepository.findRecentesByAtletaAndTenant(
                atletaId, tenantId, PageRequest.of(0, 1));
        if (recentes.isEmpty()) {
            return Optional.empty();
        }
        RevisaoSemanal revisao = recentes.get(0);
        LocalDate semanaFimRevisao = revisao.getPlanoSemanal() != null
                ? revisao.getPlanoSemanal().getSemanaFim()
                : null;

        if (!RevisaoSemanalCalculator.withinConsumptionWindow(semanaFimRevisao, semanaInicioPlano)) {
            log.debug("[weekly-review] revisão {} fora da janela (fim {} vs início do plano {}) — não consome",
                    revisao.getId(), semanaFimRevisao, semanaInicioPlano);
            return Optional.empty();
        }
        return Optional.of(revisao);
    }
}
