package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.planner.RacePreparationRule;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.DistanciaProva;
import org.springframework.stereotype.Component;

/**
 * Preenche os campos derivados de uma prova antes de gravar — ponto único para CRUD e onboarding
 * (spec prova-preparacao-minima, "Campos derivados preenchidos em toda gravação").
 */
@Component
public class ProvaEnricher {

    /**
     * Idempotent: YES — reaplicar produz os mesmos valores.
     * Side Effects: mutação da entidade em memória (sem persistência).
     * Tenant-aware: N/A.
     *
     * <p>{@code distanciaKm} só é preenchido quando vier vazio e a distância for padrão;
     * {@code semanasPreparacao} e {@code inicioPreparacao} são sempre recalculados, ignorando
     * qualquer valor vindo do cliente.</p>
     */
    public void aplicarDerivados(Prova prova) {
        if (prova == null) {
            throw new IllegalArgumentException("Prova não pode ser nula");
        }
        if (prova.getDistancia() == null || prova.getDataProva() == null) {
            throw new IllegalArgumentException("Prova precisa de distancia e dataProva para derivar a preparação");
        }
        if (prova.getDistanciaKm() == null && prova.getDistancia() != DistanciaProva.CUSTOMIZADA) {
            prova.setDistanciaKm(RacePreparationRule.distanciaNominalKm(prova.getDistancia()));
        }
        int semanas = RacePreparationRule.minimoSemanas(prova.getDistancia(), prova.getDistanciaKm());
        prova.setSemanasPreparacao(semanas);
        prova.setInicioPreparacao(RacePreparationRule.inicioPreparacao(prova.getDataProva(), semanas));
    }
}
