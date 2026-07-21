package br.com.menthoros.backend.services.onboarding.impl;

import br.com.menthoros.backend.entity.AtividadeProvenienciaDescartada;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.repository.AtividadeProvenienciaDescartadaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.onboarding.ActivityDedupService;
import br.com.menthoros.backend.services.onboarding.FontePriority;
import br.com.menthoros.backend.services.onboarding.NormalizedActivity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Deduplica atividades entre fontes para o calculo do baseline (design.md
 * Decisao 2, athlete-onboarding-baseline) — leitura, nao ingestao. Ver
 * javadoc de {@link ActivityDedupService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityDedupServiceImpl implements ActivityDedupService {

    private static final double TOLERANCIA_SIMILARIDADE = 0.05; // +-5%, design.md Decisao 2

    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final AtividadeProvenienciaDescartadaRepository provenienciaRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public List<NormalizedActivity> deduplicar(List<NormalizedActivity> historico, UUID tenantId) {
        if (historico == null || historico.isEmpty()) {
            return List.of();
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId nao pode ser nulo");
        }

        Map<java.time.LocalDate, List<NormalizedActivity>> porDia = historico.stream()
                .collect(Collectors.groupingBy(NormalizedActivity::date));

        List<NormalizedActivity> resultado = new ArrayList<>();
        for (List<NormalizedActivity> doDia : porDia.values()) {
            resultado.addAll(deduplicarDentroDoDia(doDia, tenantId));
        }
        return resultado;
    }

    private List<NormalizedActivity> deduplicarDentroDoDia(List<NormalizedActivity> doDia, UUID tenantId) {
        List<NormalizedActivity> restantes = new ArrayList<>(doDia);
        List<NormalizedActivity> resultado = new ArrayList<>();

        while (!restantes.isEmpty()) {
            NormalizedActivity atual = restantes.remove(0);
            List<NormalizedActivity> duplicatas = new ArrayList<>();
            for (NormalizedActivity outra : new ArrayList<>(restantes)) {
                if (saoSimilares(atual, outra)) {
                    duplicatas.add(outra);
                    restantes.remove(outra);
                }
            }

            if (duplicatas.isEmpty()) {
                resultado.add(atual);
                continue;
            }

            duplicatas.add(atual);
            NormalizedActivity vencedora = duplicatas.stream()
                    .min((a, b) -> Integer.compare(prioridade(a.source()), prioridade(b.source())))
                    .orElseThrow();
            for (NormalizedActivity descartada : duplicatas) {
                if (descartada != vencedora) {
                    registrarAuditoria(vencedora, descartada, tenantId);
                }
            }
            resultado.add(vencedora);
        }
        return resultado;
    }

    private boolean saoSimilares(NormalizedActivity a, NormalizedActivity b) {
        if (a.durationMinutes() == null || b.durationMinutes() == null
                || a.distanceKm() == null || b.distanceKm() == null) {
            return false; // sem dado suficiente para comparar — nao assume duplicata
        }
        boolean duracaoSimilar = dentroDaTolerancia(a.durationMinutes(), b.durationMinutes());
        boolean distanciaSimilar = dentroDaTolerancia(a.distanceKm(), b.distanceKm());
        return duracaoSimilar && distanciaSimilar;
    }

    private boolean dentroDaTolerancia(Number x, Number y) {
        double a = x.doubleValue();
        double b = y.doubleValue();
        double base = Math.max(Math.abs(a), Math.abs(b));
        if (base == 0) {
            return a == b;
        }
        return Math.abs(a - b) / base <= TOLERANCIA_SIMILARIDADE;
    }

    private int prioridade(FonteDados fonte) {
        return FontePriority.de(fonte);
    }

    private void registrarAuditoria(NormalizedActivity vencedora, NormalizedActivity descartada, UUID tenantId) {
        TreinoRealizado treinoVencedor = treinoRealizadoRepository.findById(vencedora.treinoRealizadoId())
                .orElseThrow(() -> new IllegalStateException(
                        "TreinoRealizado vencedor nao encontrado: " + vencedora.treinoRealizadoId()));

        AtividadeProvenienciaDescartada auditoria = new AtividadeProvenienciaDescartada();
        auditoria.setAtividade(treinoVencedor);
        auditoria.setTenantId(tenantId);
        auditoria.setFonteDescartada(descartada.source());
        auditoria.setDadosDescartados(serializar(descartada));
        auditoria.setMotivoDescarte("Dedup: atividade duplicada de menor prioridade de fonte (mesmo dia, duracao/distancia dentro de +-5%)");

        provenienciaRepository.save(auditoria);
        log.info("Dedup: descartada atividade {} (fonte {}) em favor de {} (fonte {})",
                descartada.activityId(), descartada.source(), vencedora.activityId(), vencedora.source());
    }

    private String serializar(NormalizedActivity atividade) {
        try {
            return objectMapper.writeValueAsString(atividade);
        } catch (Exception e) {
            log.warn("Falha ao serializar atividade descartada para auditoria: {}", e.getMessage());
            return "{}";
        }
    }
}
