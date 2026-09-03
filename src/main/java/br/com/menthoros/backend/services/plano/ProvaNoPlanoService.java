package br.com.menthoros.backend.services.plano;

import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.util.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Único dono da regra "prova ↔ treino planejado" (design.md D2, prova-no-plano-semanal): garante
 * que toda prova não cancelada da semana vire o treino do dia — na geração e quando a prova
 * entra numa semana já gerada (tasks 3.x).
 *
 * <p>{@code garantirProvasNaSemana} roda em DTO (mesmo nível da redistribuição de
 * {@link br.com.menthoros.backend.services.helper.RedistribuicaoTreinoHelper}); o vínculo por
 * {@code provaId} só vira entidade no {@link TreinoMapper}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProvaNoPlanoService {

    private static final BigDecimal PACE_FALLBACK_MIN_KM = BigDecimal.valueOf(6);
    private static final String ZONA_ALVO_PROVA = "Zona 3-4";

    private final ProvaRepository provaRepository;
    private final TreinoMapper treinoMapper;

    /**
     * Idempotent: YES — reaplicar sobre o mesmo `treinosLlm` produz o mesmo resultado (a prova
     * é buscada de novo e substitui o dia de novo).
     * Side Effects: NONE (opera em DTOs em memória; nenhuma leitura fora de `provaRepository`).
     * Tenant-aware: N/A — `atleta` já resolvido pelo chamador dentro do tenant corrente.
     *
     * <p>Para cada prova não cancelada do atleta com {@code dataProva} em
     * {@code [semanaInicio, semanaFim]}, remove os DTOs do mesmo {@code diaSemana} e insere o
     * treino {@code PROVA} construído por {@link #construirTreinoProva}. Prova cancelada não
     * gera treino. Duas provas no mesmo dia geram dois treinos {@code PROVA} (risco aceito no
     * design — caso raro, sem regra especial).
     */
    public List<TreinoPlanejadoLlmDto> garantirProvasNaSemana(List<TreinoPlanejadoLlmDto> treinosLlm,
                                                               Atleta atleta,
                                                               LocalDate semanaInicio,
                                                               LocalDate semanaFim) {
        List<Prova> provasDaSemana = provaRepository
                .findByAtletaAndDataProvaBetweenOrderByDataProvaAsc(atleta, semanaInicio, semanaFim);

        if (provasDaSemana.isEmpty()) {
            return treinosLlm;
        }

        List<TreinoPlanejadoLlmDto> resultado = new java.util.ArrayList<>(treinosLlm);

        for (Prova prova : provasDaSemana) {
            DiaSemana diaDaProva = Utils.converterDayOfWeekParaDiaSemana(prova.getDataProva().getDayOfWeek());
            String diaDaProvaNome = diaDaProva.name();

            resultado = resultado.stream()
                    .filter(t -> !diaDaProvaNome.equals(t.diaSemana()))
                    .collect(Collectors.toCollection(java.util.ArrayList::new));

            resultado.add(construirTreinoProva(prova, atleta));

            log.info("Prova '{}' garantida no plano: dia={}, provaId={}",
                    prova.getNomeProva(), diaDaProvaNome, prova.getId());
        }

        return resultado;
    }

    /**
     * Idempotent: YES — determinístico para a mesma prova/atleta.
     * Side Effects: NONE.
     * Tenant-aware: N/A.
     *
     * <p>Constrói o DTO do treino {@code PROVA}: nome e distância vêm da prova, ritmo e duração
     * do tempo objetivo quando houver (fallback pace de limiar do atleta × distância; sem
     * limiar, 6:00 min/km). Nunca lê dados do LLM.
     */
    public TreinoPlanejadoLlmDto construirTreinoProva(Prova prova, Atleta atleta) {
        DiaSemana diaSemana = Utils.converterDayOfWeekParaDiaSemana(prova.getDataProva().getDayOfWeek());
        double distanciaKm = prova.getDistanciaKm() != null ? prova.getDistanciaKm().doubleValue() : 0.0;

        Duration duracao;
        String ritmoAlvo;
        if (prova.getTempoObjetivo() != null) {
            duracao = prova.getTempoObjetivo();
            ritmoAlvo = formatarRitmo(paceSegundosPorKm(duracao, distanciaKm));
        } else {
            BigDecimal paceMinPorKm = atleta.getPaceLimiar() != null ? atleta.getPaceLimiar() : PACE_FALLBACK_MIN_KM;
            long segundosPorKm = paceMinPorKm.multiply(BigDecimal.valueOf(60))
                    .setScale(0, RoundingMode.HALF_UP).longValue();
            duracao = Duration.ofSeconds(Math.round(segundosPorKm * distanciaKm));
            ritmoAlvo = formatarRitmo(segundosPorKm);
        }

        return new TreinoPlanejadoLlmDto(
                diaSemana.name(), "PROVA", null, null, null, null, null,
                treinoMapper.durationToString(duracao), distanciaKm, ritmoAlvo, null,
                prova.getNomeProva(), ZONA_ALVO_PROVA, prova.getId());
    }

    private long paceSegundosPorKm(Duration duracao, double distanciaKm) {
        if (distanciaKm <= 0) {
            return 0;
        }
        return Math.round(duracao.getSeconds() / distanciaKm);
    }

    private String formatarRitmo(long segundosPorKm) {
        long minutos = segundosPorKm / 60;
        long segundos = segundosPorKm % 60;
        return String.format("%d:%02d", minutos, segundos);
    }
}
