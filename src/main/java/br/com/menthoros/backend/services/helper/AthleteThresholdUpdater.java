package br.com.menthoros.backend.services.helper;

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
        List<TreinoRealizado> treinos30d = treinoRealizadoRepository
                .findByAtletaIdAndTenantIdAndDataTreinoBetween(atletaId, tenantId, hoje.minusDays(30), hoje);

        if (fcStale) {
            thresholdInferenceService.inferirFcLimiar(treinos30d, hoje)
                    .ifPresent(est -> {
                        metaDados.setFcLimiarEstimado(est.valor());
                        metaDados.setConfiancaInferenciaFc(est.confianca());
                        metaDados.setDataInferenciaLimiar(hoje);
                    });
        }
        if (paceStale) {
            atualizarPaceLimiarInferido(atletaId, tenantId, metaDados, hoje, treinos30d);
        }
    }

    /**
     * Deriva `paceLimiarEstimado`: se existir uma prova válida recente (5000-21097m, dentro dos
     * últimos {@link ThresholdInferenceService#DIAS_LIMIAR_DESATUALIZACAO} dias), ela tem
     * precedência sobre a inferência passiva por quintil (design.md D3). Sem prova válida,
     * comportamento idêntico ao anterior a esta change.
     *
     * Idempotent: NO — grava `paceLimiarEstimado`/`fonteLimiarPace` em `metaDados`.
     * Side Effects: NONE (mutação em memória; persistência é responsabilidade do caller).
     * Tenant-aware: YES — busca de provas restrita a `tenantId`.
     */
    private void atualizarPaceLimiarInferido(UUID atletaId, UUID tenantId, PlanoMetaDados metaDados,
                                              LocalDate hoje, List<TreinoRealizado> treinos30d) {
        List<Prova> provasCandidatas = provaRepository.findProvasRealizadasRecentes(
                atletaId, tenantId, hoje.minusDays(ThresholdInferenceService.DIAS_LIMIAR_DESATUALIZACAO));
        Optional<Prova> provaValida = thresholdInferenceService.encontrarProvaValidaMaisRecente(provasCandidatas);

        if (provaValida.isPresent()) {
            Prova prova = provaValida.get();
            BigDecimal paceAntigo = metaDados.getPaceLimiarEstimado();
            BigDecimal paceNovo = thresholdInferenceService.inferirPaceLimiarDeProva(prova);
            logSinalizacaoOutlierPace(atletaId, paceAntigo, paceNovo, prova.getId());

            metaDados.setPaceLimiarEstimado(paceNovo);
            // ALTA fixo (não amostral como no quintil): esforço deliberado e máximo de uma prova
            // real é sempre mais confiável que a mediana de treinos incidentais (design.md D3).
            metaDados.setConfiancaInferenciaPace(ConfiancaInferencia.ALTA);
            metaDados.setFonteLimiarPace(FonteLimiarInferencia.PROVA_REGISTRADA);
            metaDados.setDataInferenciaLimiar(hoje);
            return;
        }

        thresholdInferenceService.inferirPaceLimiar(treinos30d, hoje)
                .ifPresent(est -> {
                    metaDados.setPaceLimiarEstimado(est.valor());
                    metaDados.setConfiancaInferenciaPace(est.confianca());
                    metaDados.setFonteLimiarPace(FonteLimiarInferencia.MEDIA_TREINOS);
                    metaDados.setDataInferenciaLimiar(hoje);
                });
    }

    /**
     * Sinaliza (log, não bloqueia) quando a variação de `paceLimiarEstimado` derivado de uma
     * prova excede {@link #LIMIAR_OUTLIER_SEC_KM} — indica prova mal cadastrada ou offset
     * inadequado para o perfil do atleta, para revisão manual do founder/coach (design.md D5).
     */
    private void logSinalizacaoOutlierPace(UUID atletaId, BigDecimal paceAntigo, BigDecimal paceNovo, UUID provaId) {
        if (paceAntigo == null) {
            log.info("atualizarLimiares: paceLimiarEstimado calculado pela primeira vez via prova. "
                    + "atletaId={}, provaId={}, paceNovo={}", atletaId, provaId, paceNovo);
            return;
        }
        BigDecimal deltaSegundosPorKm = paceNovo.subtract(paceAntigo).multiply(BigDecimal.valueOf(60));
        if (deltaSegundosPorKm.abs().compareTo(LIMIAR_OUTLIER_SEC_KM) > 0) {
            log.warn("atualizarLimiares: variação de paceLimiarEstimado acima do limiar de outlier (D5). "
                    + "atletaId={}, paceAntigo={}, paceNovo={}, deltaSegKm={}, provaId={}",
                    atletaId, paceAntigo, paceNovo, deltaSegundosPorKm, provaId);
        } else {
            log.info("atualizarLimiares: paceLimiarEstimado atualizado via prova. "
                    + "atletaId={}, paceAntigo={}, paceNovo={}, deltaSegKm={}, provaId={}",
                    atletaId, paceAntigo, paceNovo, deltaSegundosPorKm, provaId);
        }
    }
}
