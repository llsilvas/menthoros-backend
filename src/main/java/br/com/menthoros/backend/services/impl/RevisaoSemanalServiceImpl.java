package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.RevisaoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.NivelAderencia;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.RecommendationType;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.RevisaoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.RevisaoSemanalService;
import br.com.menthoros.backend.services.helper.RevisaoSemanalCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Consolidação determinística da revisão (blocos 2–3). Conta aderência na janela EXATA do plano,
 * delega a decisão ao {@link RevisaoSemanalCalculator} e congela o resultado no encerramento.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RevisaoSemanalServiceImpl implements RevisaoSemanalService {

    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final RevisaoSemanalRepository revisaoSemanalRepository;
    private final PlanoSemanalRepository planoSemanalRepository;

    @Override
    public RevisaoSemanal consolidar(PlanoSemanal plano) {
        UUID atletaId = plano.getAtleta().getId();
        UUID tenantId = plano.getAssessoria().getId();

        // findComRealizadoByAtletaAndPeriodo não tem limite superior; recorta à janela EXATA do
        // plano (dataTreino ≤ semanaFim) em memória, para não contar treinos de semanas futuras.
        List<TreinoPlanejado> treinos = treinoPlanejadoRepository
                .findComRealizadoByAtletaAndPeriodo(atletaId, tenantId, plano.getSemanaInicio())
                .stream()
                .filter(t -> t.getDataTreino() != null && !t.getDataTreino().isAfter(plano.getSemanaFim()))
                .toList();

        int planejados = treinos.size();
        int realizados = (int) treinos.stream()
                .filter(t -> t.getTreinoRealizado() != null)
                .count();
        boolean criticoFaltando = treinos.stream().anyMatch(t ->
                t.getTreinoRealizado() == null
                        && t.getTipoTreino() != null
                        && RevisaoSemanalCalculator.treinoCritico(t.getTipoTreino().getFatorImpacto()));

        BigDecimal tsbFim = plano.getTsbFim();
        BigDecimal percentual = RevisaoSemanalCalculator.percentualRealizacao(planejados, realizados);
        NivelAderencia aderencia = RevisaoSemanalCalculator.nivelAderencia(percentual, criticoFaltando);
        boolean dadosSuficientes = RevisaoSemanalCalculator.dadosSuficientes(realizados, tsbFim);
        RecommendationType recommendationType =
                RevisaoSemanalCalculator.recommendationType(aderencia, tsbFim, dadosSuficientes);

        return RevisaoSemanal.builder()
                .planoSemanal(plano)
                .recommendationType(recommendationType)
                .adherenceStatus(aderencia)
                .percentualRealizacao(percentual)
                .dadosSuficientes(dadosSuficientes)
                .geradaEm(Instant.now())
                .build();
    }

    @Override
    @Transactional
    public void gerarNoEncerramento(UUID planoId, UUID tenantId) {
        PlanoSemanal plano = planoSemanalRepository.findByIdAndTenantId(planoId, tenantId).orElse(null);
        if (plano == null || plano.getStatus() != PlanoStatus.CONCLUIDO) {
            return; // plano inexistente ou evento de perdidos-only (semana não fechada)
        }
        if (revisaoSemanalRepository.findByPlanoSemanalIdAndTenant(planoId, tenantId).isPresent()) {
            return; // já congelada — idempotente, preserva o congelamento (ADR-0006 / CA6)
        }
        revisaoSemanalRepository.save(consolidar(plano));
        log.info("[revisao-semanal] revisão gerada no encerramento do plano {} (tenant {})", planoId, tenantId);
    }
}
