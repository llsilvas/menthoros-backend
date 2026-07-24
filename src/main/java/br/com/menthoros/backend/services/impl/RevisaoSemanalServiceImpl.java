package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.RevisaoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.NivelAderencia;
import br.com.menthoros.backend.enums.RecommendationType;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.RevisaoSemanalService;
import br.com.menthoros.backend.services.helper.RevisaoSemanalCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Consolidação determinística da revisão (bloco 2). Conta aderência na janela EXATA do plano
 * ({@code findComRealizadoByAtletaAndJanela}) e delega a decisão ao {@link RevisaoSemanalCalculator}.
 * Não persiste — o hook de encerramento e a idempotência entram no bloco 3.
 */
@Service
@RequiredArgsConstructor
public class RevisaoSemanalServiceImpl implements RevisaoSemanalService {

    private final TreinoPlanejadoRepository treinoPlanejadoRepository;

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
}
