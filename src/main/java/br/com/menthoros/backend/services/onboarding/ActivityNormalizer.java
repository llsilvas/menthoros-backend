package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.entity.TreinoRealizado;

/**
 * Converte um {@link TreinoRealizado} (ja persistido por qualquer conector -
 * Strava, .fit, intervals.icu, manual) para a estrutura canonica
 * {@link NormalizedActivity} (design.md Decisao 1, athlete-onboarding-baseline).
 *
 * <p>Nao reimplementa ingestao: cada conector ja filtra modalidade
 * (Run/TrailRun/VirtualRun/Treadmill) antes de persistir — por isso este
 * normalizador recebe um unico parametro (a fonte ja esta em
 * {@code treino.getFonteDados()}, sem precisar de um {@code DataSource}
 * separado).
 */
public interface ActivityNormalizer {

    NormalizedActivity toCanonical(TreinoRealizado treino);
}
