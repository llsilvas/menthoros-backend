package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.output.MelhorEsforcoDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ConfiancaInferencia;
import br.com.menthoros.backend.enums.FonteLimiarInferencia;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquestra a atualização dos limiares inferidos (FC/pace) de um atleta — extraído de
 * {@code TsbServiceImpl} (design.md da change refactor-threshold-orchestration) para dar um seam
 * público testável ao que antes só existia como métodos privados alcançáveis via reflection.
 *
 * <p>Idempotent: NO — grava {@code fcLimiarEstimado}/{@code paceLimiarEstimado}/
 * {@code fonteLimiarPace} em {@code metaDados} quando o limiar oficial está desatualizado.
 * <p>Side Effects: NONE — mutação em memória de {@code metaDados}; persistência continua sendo
 * responsabilidade do caller ({@code TsbServiceImpl.atualizarMetaDados}, via
 * {@code planoMetaDadosRepository.save(...)}).
 * <p>Tenant-aware: YES — busca de treinos/provas restrita ao {@code tenantId} do
 * {@code atleta.getAssessoria()}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AthleteThresholdUpdater {

    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final ProvaRepository provaRepository;
    private final ThresholdInferenceService thresholdInferenceService;

    /** Limiar de variação de paceLimiarEstimado que sinaliza outlier para revisão manual (design.md D5). */
    private static final BigDecimal LIMIAR_OUTLIER_SEC_KM = BigDecimal.valueOf(20);

    public void atualizarLimiares(Atleta atleta, PlanoMetaDados metaDados, LocalDate hoje) {
        if (atleta == null) {
            throw new IllegalArgumentException("Atleta não pode ser nulo");
        }
        UUID atletaId = atleta.getId();
        boolean fcStale = thresholdInferenceService.isFcLimiarDesatualizado(atleta, hoje);
        boolean paceStale = thresholdInferenceService.isPaceLimiarDesatualizado(atleta, hoje);

        if (!fcStale && !paceStale) return;

        if (atleta.getAssessoria() == null) {
            log.warn("atualizarLimiares: atleta {} sem assessoria — inferência ignorada", atletaId);
            return;
        }
        UUID tenantId = atleta.getAssessoria().getId();
        List<TreinoRealizado> treinos30d = buscarTreinos30d(atletaId, tenantId, hoje);

        if (fcStale) {
            aplicarFcSeDesatualizado(metaDados, treinos30d, hoje);
        }
        if (paceStale) {
            BigDecimal paceLimiarAnterior = metaDados.getPaceLimiarEstimado();
            // Caminho legado (consolidação de recalcularHistoricoCompleto) sem acesso a
            // MelhorEsforcoService — fora de escopo desta change (design.md D4), lista vazia
            // mantém o comportamento de 2 fontes (prova/quintil) aqui.
            PaceLimiarResolvido resolvido = resolverFontePace(
                    atletaId, tenantId, hoje, treinos30d, paceLimiarAnterior, List.of()).orElse(null);
            aplicarPaceLimiar(metaDados, resolvido, hoje);
        }
    }

    /**
     * Equivalente a {@code atualizarLimiares}, só pra FC — extraído pra dar ao {@code
     * TsbDiaPersister} (refactor-threshold-call-outside-transaction, seção 4) um jeito de tratar
     * FC dentro da transação sem repetir a resolução de pace, que já chega pré-resolvida de fora
     * dela. FC não ganha a separação decisão/aplicação de {@link #resolverFontePace} (design.md
     * D3 — sem 3ª fonte de FC no roadmap, YAGNI); só precisava ser chamável isoladamente.
     *
     * Idempotent: NO — grava `fcLimiarEstimado`/`confiancaInferenciaFc` em `metaDados` quando
     * desatualizado.
     * Side Effects: NONE (mutação em memória; persistência é responsabilidade do caller).
     * Tenant-aware: YES — busca de treinos restrita ao `tenantId` do `atleta.getAssessoria()`.
     */
    public void atualizarFcLimiar(Atleta atleta, PlanoMetaDados metaDados, LocalDate hoje) {
        if (atleta == null) {
            throw new IllegalArgumentException("Atleta não pode ser nulo");
        }
        if (!thresholdInferenceService.isFcLimiarDesatualizado(atleta, hoje)) return;
        if (atleta.getAssessoria() == null) {
            log.warn("atualizarFcLimiar: atleta {} sem assessoria — inferência ignorada", atleta.getId());
            return;
        }
        List<TreinoRealizado> treinos30d = buscarTreinos30d(atleta.getId(), atleta.getAssessoria().getId(), hoje);
        aplicarFcSeDesatualizado(metaDados, treinos30d, hoje);
    }

    private void aplicarFcSeDesatualizado(PlanoMetaDados metaDados, List<TreinoRealizado> treinos30d, LocalDate hoje) {
        thresholdInferenceService.inferirFcLimiar(treinos30d, hoje)
                .ifPresent(est -> {
                    metaDados.setFcLimiarEstimado(est.valor());
                    metaDados.setConfiancaInferenciaFc(est.confianca());
                    metaDados.setDataInferenciaLimiar(hoje);
                });
    }

    // D8 (ingestao-treino-realizado): cancelado não conta na carga — mesmo predicado usado por
    // TsbService/produtores; achado do /qa do Bloco 2 (Codex adversarial-review, 2026-08-24) —
    // esta query alimenta a inferência de limiares de FC/pace e ficara de fora do inventário
    // original da task 7.7.
    //
    /**
     * Treinos dos últimos 30 dias que contam na carga (D8) — exposto `public` (achado de QA,
     * clean-code-reviewer, refactor-threshold-call-outside-transaction) porque
     * {@code TsbServiceImpl.resolverPaceSeNecessario} (pacote {@code services.impl}, diferente
     * deste) precisa da mesma query fora da transação; reaproveitar evita duplicar a query + o
     * filtro `contaNaCarga`.
     *
     * Idempotent: YES · Side Effects: NONE
     */
    public List<TreinoRealizado> buscarTreinos30d(UUID atletaId, UUID tenantId, LocalDate hoje) {
        return treinoRealizadoRepository
                .findByAtletaIdAndTenantIdAndDataTreinoBetween(atletaId, tenantId, hoje.minusDays(30), hoje)
                .stream()
                .filter(TreinoRealizado::contaNaCarga)
                .toList();
    }

    /**
     * Seleciona a marca elegível (5k/10k) dentro da lista de melhores esforços recentes do
     * atleta — 10k vence quando ambos presentes (mesma faixa de distância válida pra prova,
     * design.md D2, use-best-effort-for-threshold-inference).
     *
     * Idempotent: YES · Side Effects: NONE
     */
    public Optional<MelhorEsforcoDto> encontrarMelhorEsforcoValido(List<MelhorEsforcoDto> marcas) {
        if (marcas == null) return Optional.empty();
        return marcas.stream()
                .filter(m -> "10k".equals(m.distanciaLabel()) || "5k".equals(m.distanciaLabel()))
                .min(Comparator.comparing(m -> "10k".equals(m.distanciaLabel()) ? 0 : 1));
    }

    /**
     * Decide qual fonte de `paceLimiarEstimado` vence: se existir uma prova válida recente
     * (5000-21097m, dentro dos últimos {@link ThresholdInferenceService#DIAS_LIMIAR_DESATUALIZACAO}
     * dias), ela tem precedência sobre o melhor esforço recente (5k/10k, janela de 42 dias), que
     * por sua vez tem precedência sobre a inferência passiva por quintil (design.md D1/D3,
     * use-best-effort-for-threshold-inference). Puro — sem `PlanoMetaDados`, sem mutação
     * (refactor-threshold-call-outside-transaction, design.md D1): `paceLimiarAnterior` é só o
     * valor usado pelo log de outlier (D5), não a entidade.
     *
     * Idempotent: YES · Side Effects: NONE (o log de outlier é observabilidade, não estado)
     * Tenant-aware: YES — busca de provas restrita a `tenantId`.
     */
    public Optional<PaceLimiarResolvido> resolverFontePace(UUID atletaId, UUID tenantId, LocalDate hoje,
                                                             List<TreinoRealizado> treinos30d,
                                                             BigDecimal paceLimiarAnterior,
                                                             List<MelhorEsforcoDto> melhoresEsforcos) {
        List<Prova> provasCandidatas = provaRepository.findProvasRealizadasRecentes(
                atletaId, tenantId, hoje.minusDays(ThresholdInferenceService.DIAS_LIMIAR_DESATUALIZACAO));
        Optional<Prova> provaValida = thresholdInferenceService.encontrarProvaValidaMaisRecente(provasCandidatas);

        if (provaValida.isPresent()) {
            Prova prova = provaValida.get();
            BigDecimal paceNovo = thresholdInferenceService.inferirPaceLimiarDeProva(prova);
            logSinalizacaoOutlierPace(atletaId, paceLimiarAnterior, paceNovo, "provaId=" + prova.getId());

            // ALTA fixo (não amostral como no quintil): esforço deliberado e máximo de uma prova
            // real é sempre mais confiável que a mediana de treinos incidentais (design.md D3).
            return Optional.of(new PaceLimiarResolvido(
                    FonteLimiarInferencia.PROVA_REGISTRADA, paceNovo, ConfiancaInferencia.ALTA));
        }

        Optional<MelhorEsforcoDto> melhorEsforcoValido = encontrarMelhorEsforcoValido(melhoresEsforcos);
        if (melhorEsforcoValido.isPresent()) {
            MelhorEsforcoDto marca = melhorEsforcoValido.get();
            BigDecimal paceNovo = thresholdInferenceService.inferirPaceLimiarDeMelhorEsforco(marca);
            logSinalizacaoOutlierPace(atletaId, paceLimiarAnterior, paceNovo,
                    "distanciaLabel=" + marca.distanciaLabel() + ", tempoSegundos=" + marca.tempoSegundos());

            // ALTA fixo, mesmo raciocínio da prova: marca controlada de 42d é mais confiável que
            // a mediana de treinos incidentais (design.md D3).
            return Optional.of(new PaceLimiarResolvido(
                    FonteLimiarInferencia.MELHOR_ESFORCO, paceNovo, ConfiancaInferencia.ALTA));
        }

        return thresholdInferenceService.inferirPaceLimiar(treinos30d, hoje)
                .map(est -> new PaceLimiarResolvido(FonteLimiarInferencia.MEDIA_TREINOS, est.valor(), est.confianca()));
    }

    /**
     * Aplica um `PaceLimiarResolvido` em `metaDados` — só a mutação, sem lógica de decisão
     * (refactor-threshold-call-outside-transaction, design.md D1). `resolvido` nulo/vazio não
     * altera nada (equivalente a "nenhuma fonte disponível").
     *
     * Idempotent: NO — grava campos em `metaDados`.
     * Side Effects: NONE (mutação em memória; persistência é responsabilidade do caller).
     */
    public void aplicarPaceLimiar(PlanoMetaDados metaDados, PaceLimiarResolvido resolvido, LocalDate hoje) {
        if (resolvido == null) return;
        metaDados.setPaceLimiarEstimado(resolvido.valor());
        metaDados.setConfiancaInferenciaPace(resolvido.confianca());
        metaDados.setFonteLimiarPace(resolvido.fonte());
        metaDados.setDataInferenciaLimiar(hoje);
    }

    /**
     * Sinaliza (log, não bloqueia) a fonte/marca usada em cada resolução de `paceLimiarEstimado`
     * e alerta quando a variação excede {@link #LIMIAR_OUTLIER_SEC_KM} — indica prova/marca mal
     * cadastrada ou offset inadequado para o perfil do atleta, para revisão manual do
     * founder/coach (design.md D5). `origemDescricao` identifica a fonte sem acoplar o método a
     * um tipo de entidade específico — `"provaId=" + prova.getId()` ou `"distanciaLabel=" + ... +
     * ", tempoSegundos=" + ...` pro melhor esforço (design.md D7, use-best-effort-for-threshold-
     * inference: sempre loga INFO com a origem, mesmo sem `paceAntigo`, pra deixar rastro
     * auditável de qualquer marca usada — não só no caso de outlier).
     */
    private void logSinalizacaoOutlierPace(UUID atletaId, BigDecimal paceAntigo, BigDecimal paceNovo,
                                            String origemDescricao) {
        if (paceAntigo == null) {
            log.info("atualizarLimiares: paceLimiarEstimado calculado pela primeira vez. "
                    + "atletaId={}, {}, paceNovo={}", atletaId, origemDescricao, paceNovo);
            return;
        }
        BigDecimal deltaSegundosPorKm = paceNovo.subtract(paceAntigo).multiply(BigDecimal.valueOf(60));
        if (deltaSegundosPorKm.abs().compareTo(LIMIAR_OUTLIER_SEC_KM) > 0) {
            log.warn("atualizarLimiares: variação de paceLimiarEstimado acima do limiar de outlier (D5). "
                    + "atletaId={}, paceAntigo={}, paceNovo={}, deltaSegKm={}, {}",
                    atletaId, paceAntigo, paceNovo, deltaSegundosPorKm, origemDescricao);
        } else {
            log.info("atualizarLimiares: paceLimiarEstimado atualizado. "
                    + "atletaId={}, paceAntigo={}, paceNovo={}, deltaSegKm={}, {}",
                    atletaId, paceAntigo, paceNovo, deltaSegundosPorKm, origemDescricao);
        }
    }
}
