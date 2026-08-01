package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoPlanejadoTssBackup;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoTssBackupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Recalcula o {@code tssPlanejado} das linhas gravadas antes da correção do BUG-CONF-001.
 *
 * <p><b>Por que via aplicação e não por SQL na migração.</b> Reescrever a fórmula em SQL criaria uma
 * segunda fonte de verdade que divergiria na próxima mudança — que é exatamente o defeito que esta
 * change existe para corrigir. Aqui o valor sai do mesmo
 * {@link TssCalculatorService#calcularTssEstimado} que o resto do sistema usa.
 *
 * <p><b>Não roda sozinho.</b> Não é {@code ApplicationRunner} nem tem agendamento: alguém precisa
 * chamar {@link #recalcularTudo()} deliberadamente. Alterar dado existente é gate de confirmação no
 * {@code CLAUDE.md}, e um recálculo que dispara no boot tiraria essa decisão das mãos de quem
 * deveria tomá-la.
 *
 * <p><b>Reexecutável.</b> O snapshot é gravado só na primeira passagem por treino
 * ({@code existsByTreinoPlanejadoId}). Rodar duas vezes não sobrescreve o valor original com um já
 * corrigido — o que destruiria a capacidade de reverter.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TssPlanejadoRecalculator {

    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final TreinoPlanejadoTssBackupRepository backupRepository;
    private final TssCalculatorService tssCalculatorService;

    /** Resultado de uma execução, para quem chamar poder conferir contra a contagem esperada. */
    public record Resultado(int avaliados, int recalculados, int semInputs, int jaComSnapshot) {}

    /**
     * Grava o snapshot e recalcula o TSS de todo treino planejado que tenha valor.
     *
     * Idempotent: YES — reexecutar não altera o snapshot original nem produz valor diferente, já
     *   que a fórmula é determinística sobre os mesmos inputs.
     * Side Effects: Database insert (tb_treino_planejado_tss_backup) + update (tb_treino_planejado).
     * Tenant-aware: NO — é operação de manutenção sobre toda a base, executada deliberadamente
     *   fora do fluxo de request; não há TenantContext neste caminho.
     *
     * @return contagens da execução, para conferência contra o esperado no ambiente
     */
    @Transactional
    public Resultado recalcularTudo() {
        List<TreinoPlanejado> comTss = treinoPlanejadoRepository.findAll().stream()
                .filter(t -> t.getTssPlanejado() != null)
                .toList();

        int recalculados = 0;
        int semInputs = 0;
        int jaComSnapshot = 0;

        for (TreinoPlanejado treino : comTss) {
            if (treino.getDuracaoMin() == null) {
                // Sem duração não há como recomputar de forma determinística. Deixar como está é
                // melhor que estimar: um valor inventado é pior que um valor sabidamente antigo.
                semInputs++;
                continue;
            }

            if (backupRepository.existsByTreinoPlanejadoId(treino.getId())) {
                jaComSnapshot++;
                continue;
            }

            backupRepository.save(TreinoPlanejadoTssBackup.builder()
                    .treinoPlanejadoId(treino.getId())
                    .tssPlanejadoAntes(treino.getTssPlanejado())
                    .build());

            treino.setTssPlanejado(tssCalculatorService.calcularTssEstimado(
                    treino.getDuracaoMin(), treino.getPercepcaoEsforcoEsperada()));
            treinoPlanejadoRepository.save(treino);
            recalculados++;
        }

        log.info("Recálculo de tssPlanejado concluído: avaliados={}, recalculados={}, "
                        + "semInputs={}, jaComSnapshot={}",
                comTss.size(), recalculados, semInputs, jaComSnapshot);

        return new Resultado(comTss.size(), recalculados, semInputs, jaComSnapshot);
    }

    /**
     * Restaura os valores do snapshot, desfazendo o recálculo.
     *
     * Idempotent: YES — restaurar duas vezes deixa o mesmo estado.
     * Side Effects: Database update (tb_treino_planejado).
     * Tenant-aware: NO — mesma justificativa de {@link #recalcularTudo()}.
     *
     * <p>Restaura a partir do valor guardado, sem recomputar a fórmula antiga: é isso que faz disto
     * uma reversão de verdade, e que a mantém válida depois de a fórmula antiga sumir do código.
     *
     * @return quantos treinos foram restaurados
     */
    @Transactional
    public int reverter() {
        List<TreinoPlanejadoTssBackup> snapshots = backupRepository.findAll();

        int restaurados = 0;
        for (TreinoPlanejadoTssBackup snapshot : snapshots) {
            var treino = treinoPlanejadoRepository.findById(snapshot.getTreinoPlanejadoId());
            if (treino.isEmpty()) {
                continue;
            }
            treino.get().setTssPlanejado(snapshot.getTssPlanejadoAntes());
            treinoPlanejadoRepository.save(treino.get());
            restaurados++;
        }

        log.info("Reversão de tssPlanejado concluída: restaurados={} de {} snapshots",
                restaurados, snapshots.size());
        return restaurados;
    }
}
