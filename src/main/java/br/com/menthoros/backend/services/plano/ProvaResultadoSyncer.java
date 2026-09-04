package br.com.menthoros.backend.services.plano;

import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.TipoTreino;
import org.springframework.stereotype.Component;

/**
 * Fecha o resultado de uma prova a partir da execução do seu treino {@code PROVA}
 * (prova-no-plano-semanal, D6) — chamado em todo caminho que vincula um {@link TreinoRealizado} a
 * um {@link TreinoPlanejado}: registro manual do atleta, lançamento do coach, reconciliação de
 * FIT ou Strava.
 */
@Component
public class ProvaResultadoSyncer {

    /**
     * Idempotent: SIM — reaplicar com o mesmo realizado grava os mesmos valores; refazer o
     * vínculo para outro realizado atualiza o tempo para o novo.
     * Side Effects: mutação em memória da {@link Prova} vinculada (o chamador é responsável por
     * persistir — a prova costuma já estar na mesma sessão/transação do vínculo).
     * Tenant-aware: N/A — opera sobre entidades já resolvidas pelo caller.
     *
     * <p>Se {@code planejado} é do tipo {@code PROVA} e tem uma {@link Prova} vinculada, marca
     * {@code foiRealizada = true} e copia a duração do realizado para {@code tempoRealizado}.
     * Nunca desmarca — não há caminho de "desvincular" que chame este método; o contrato é
     * unidirecional de propósito (ver design.md D6, risco "reconciliação vincula errado").
     */
    public void aoVincular(TreinoPlanejado planejado, TreinoRealizado realizado) {
        if (planejado == null || realizado == null) {
            return;
        }
        if (planejado.getTipoTreino() != TipoTreino.PROVA) {
            return;
        }
        Prova prova = planejado.getProva();
        if (prova == null) {
            return;
        }
        prova.setFoiRealizada(true);
        prova.setTempoRealizado(realizado.getDuracaoMin());
    }
}
