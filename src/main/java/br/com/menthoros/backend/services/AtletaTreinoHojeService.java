package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.TreinoHojeDto;
import br.com.menthoros.backend.enums.MotivoPulo;

import java.util.Optional;
import java.util.UUID;

/**
 * O treino de hoje do atleta autenticado — modo treino. "Hoje" é no fuso do atleta.
 */
public interface AtletaTreinoHojeService {

    /**
     * @param atletaId atleta já resolvido do token
     * @return o planejado de hoje com alvos por etapa, ou vazio quando não há treino hoje
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o atleta não pertence ao tenant
     */
    Optional<TreinoHojeDto> getTreinoHoje(UUID atletaId);

    /**
     * "Não vou conseguir hoje": o planejado de hoje vai a {@code PERDIDO} com motivo (opcional) e
     * carimbo. Não cria {@code TreinoRealizado}; a reversão acontece quando um realizado vincula.
     *
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException      atleta fora do tenant
     * @throws br.com.menthoros.backend.exception.DomainRuleViolationException sem treino hoje, ou já realizado
     */
    TreinoHojeDto pularHoje(UUID atletaId, MotivoPulo motivo);
}
