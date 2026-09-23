package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.planner.WeekPlanSkeleton;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.ModoGeracaoPlano;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Responde, num lugar só, se a regra de cobertura da semana comanda esta geração
 * (add-descanso-explicito-por-fadiga, Decisão 6).
 *
 * <p>Existe porque a resposta é precisa em <b>dois</b> pontos distantes: o {@code IaServiceImpl}
 * decide se valida a cobertura, e o {@code PlanGenerationPersister} decide se pode redistribuir os
 * treinos. Enquanto o persister não sabia, ele redistribuía por cima de um plano já validado — na
 * geração real de 22/09 20:49 o descanso prescrito para quinta virou treino e o sábado ficou vazio,
 * o inverso exato do que o atleta pediu no check-in.</p>
 *
 * <p>Idempotent: YES — função pura das entradas. Side Effects: NONE. Tenant-aware: NO.</p>
 */
@Component
@RequiredArgsConstructor
public class CoberturaSemanalPolicy {

    private final RegraGeracaoTreino regraGeracaoTreino;

    /** Kill-switch da regra; desligado, tudo volta ao comportamento anterior à change. */
    @Value("${app.plano.weekly-coverage.enabled:true}")
    private boolean habilitada;

    /**
     * Dias que o plano precisa cobrir: em SEMANA_ATUAL só os que ainda não passaram, em
     * PROXIMA_SEMANA todos os dias configurados do atleta.
     */
    public List<DiaSemana> diasACobrir(Atleta atleta, ModoGeracaoPlano modo) {
        List<DiaSemana> efetivos = diasEfetivos(atleta, modo);
        return efetivos != null ? efetivos : atleta.getDiasDisponiveis();
    }

    /**
     * Dias efetivos no formato que o prompt builder espera: {@code null} em PROXIMA_SEMANA (lá a
     * lista é materializada a partir dos dias configurados).
     */
    public @Nullable List<DiaSemana> diasEfetivos(Atleta atleta, ModoGeracaoPlano modo) {
        return ModoGeracaoPlano.SEMANA_ATUAL.equals(modo)
                ? regraGeracaoTreino.filtrarDiasDisponiveis(atleta.getDiasDisponiveis(), LocalDate.now(), modo)
                : null;
    }

    /**
     * {@code true} quando a cobertura valida esta geração — e portanto a colocação dos treinos por
     * dia já é garantida, não pode ser remexida depois.
     *
     * <p>Com skeleton do planner a regra não roda: ali o planner é dono da frequência.</p>
     */
    public boolean ativa(Atleta atleta, ModoGeracaoPlano modo, @Nullable WeekPlanSkeleton skeleton) {
        if (!habilitada || skeleton != null) {
            return false;
        }
        List<DiaSemana> dias = diasACobrir(atleta, modo);
        return dias != null && !dias.isEmpty();
    }
}
