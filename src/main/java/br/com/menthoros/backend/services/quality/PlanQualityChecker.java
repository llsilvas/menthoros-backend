package br.com.menthoros.backend.services.quality;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.services.prompt.constraint.Constraint;
import br.com.menthoros.backend.services.prompt.constraint.ConstraintKey;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifica o plano gerado pelo LLM contra as {@link Constraint} declaradas, reportando
 * {@link ViolacaoQualidade} por regra violada. Offline (sem nova chamada ao LLM) e dirigido por
 * {@code key} (o algoritmo lê os {@code params} da constraint, não reimplementa o limiar).
 *
 * <p>Idempotent: YES — leitura pura, sem mutação de estado (apenas incrementa métrica).
 * Side Effects: incrementa contador Micrometer {@code violacoes_plano{key=...}}.
 * Tenant-aware: NO — opera sobre o DTO do plano, sem acesso a dados de tenant.</p>
 *
 * <p>Verifica 4 keys: {@code INTERVALADO_PROIBIDO}, {@code PACE_TETO}, {@code DIAS_PERMITIDOS},
 * {@code MAX_CONSECUTIVOS}. {@code INTERVALADO_MAX_CATEGORIA} é declarada/renderizada mas a verificação
 * fica para fatia futura (precisa do mapa de categorias de intervalado).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanQualityChecker {

    private static final Pattern PACE_TOKEN = Pattern.compile("(\\d{1,2}):(\\d{2})");

    /** Tipos intensivos proibidos sob INTERVALADO_PROIBIDO (a descrição da constraint lista todos). */
    private static final Set<TipoTreino> INTENSIVOS_PROIBIDOS =
            Set.of(TipoTreino.INTERVALADO, TipoTreino.TIRO, TipoTreino.SUBIDA, TipoTreino.FARTLEK);

    private final MeterRegistry meterRegistry;

    public List<ViolacaoQualidade> check(PlanoSemanalLlmDto plano, List<Constraint> constraints) {
        List<ViolacaoQualidade> violacoes = new ArrayList<>();
        if (plano == null || plano.treinosPlanejados() == null || constraints == null) {
            return violacoes;
        }
        List<TreinoPlanejadoLlmDto> treinos = plano.treinosPlanejados();

        for (Constraint c : constraints) {
            switch (c.key()) {
                case INTERVALADO_PROIBIDO -> verificarIntervaladoProibido(treinos, violacoes);
                case PACE_TETO -> verificarPaceTeto(treinos, c, violacoes);
                case DIAS_PERMITIDOS -> verificarDias(treinos, c, violacoes);
                case MAX_CONSECUTIVOS -> verificarMaxConsecutivos(treinos, c, violacoes);
                case INTERVALADO_MAX_CATEGORIA -> { /* declarada, verificação adiada (fatia futura) */ }
            }
        }

        for (ViolacaoQualidade v : violacoes) {
            Counter.builder("violacoes_plano").tag("key", v.key().name()).register(meterRegistry).increment();
        }
        if (!violacoes.isEmpty()) {
            log.warn("PlanQualityChecker: {} violação(ões) de constraint no plano gerado", violacoes.size());
        }
        return violacoes;
    }

    private void verificarIntervaladoProibido(List<TreinoPlanejadoLlmDto> treinos, List<ViolacaoQualidade> out) {
        for (TreinoPlanejadoLlmDto t : treinos) {
            TipoTreino tipo = parseTipo(t.tipoTreino());
            if (tipo != null && INTENSIVOS_PROIBIDOS.contains(tipo)) {
                out.add(new ViolacaoQualidade(ConstraintKey.INTERVALADO_PROIBIDO,
                        "Treino intensivo " + tipo.name() + " em " + t.diaSemana() + " sob INTERVALADO_PROIBIDO"));
            }
        }
    }

    private void verificarPaceTeto(List<TreinoPlanejadoLlmDto> treinos, Constraint c, List<ViolacaoQualidade> out) {
        var teto = c.tetoPorTipo();
        for (TreinoPlanejadoLlmDto t : treinos) {
            TipoTreino tipo = parseTipo(t.tipoTreino());
            if (tipo == null) continue;
            BigDecimal limite = teto.get(tipo);
            if (limite == null) continue;

            parsePaceMaisRapido(t.ritmoAlvo()).ifPresent(pace -> {
                if (pace.compareTo(limite) < 0) {
                    out.add(new ViolacaoQualidade(c.key(),
                            String.format("Treino %s em %s com pace %s mais rápido que o teto %s",
                                    tipo.name(), t.diaSemana(), pace.toPlainString(), limite.toPlainString())));
                }
            });
            if (t.etapas() != null) {
                for (EtapaTreinoLlmDto e : t.etapas()) {
                    parsePaceMaisRapido(e.ritmoAlvo()).ifPresent(pace -> {
                        if (pace.compareTo(limite) < 0) {
                            out.add(new ViolacaoQualidade(c.key(),
                                    String.format("Etapa de %s em %s com pace %s mais rápido que o teto %s",
                                            tipo.name(), t.diaSemana(), pace.toPlainString(), limite.toPlainString())));
                        }
                    });
                }
            }
        }
    }

    private void verificarDias(List<TreinoPlanejadoLlmDto> treinos, Constraint c, List<ViolacaoQualidade> out) {
        List<DiaSemana> permitidos = c.diasPermitidos();
        if (permitidos.isEmpty()) return;
        for (TreinoPlanejadoLlmDto t : treinos) {
            DiaSemana dia = parseDia(t.diaSemana());
            if (dia != null && !permitidos.contains(dia)) {
                out.add(new ViolacaoQualidade(c.key(),
                        "Treino em " + t.diaSemana() + " fora dos dias permitidos " + permitidos));
            }
        }
    }

    private void verificarMaxConsecutivos(List<TreinoPlanejadoLlmDto> treinos, Constraint c, List<ViolacaoQualidade> out) {
        Integer n = c.maxConsecutivos();
        if (n == null) return;
        // Dias da semana de treino (segunda→domingo) com carga (≠ REGENERATIVO).
        // DiaSemana.order é DOMINGO=0..SABADO=6; remapeia p/ SEGUNDA=0..DOMINGO=6 via (order+6)%7.
        boolean[] cargaNoDia = new boolean[7];
        for (TreinoPlanejadoLlmDto t : treinos) {
            DiaSemana dia = parseDia(t.diaSemana());
            if (dia == null) continue;
            if (TipoTreino.REGENERATIVO.name().equalsIgnoreCase(t.tipoTreino())) continue;
            cargaNoDia[(dia.getOrder() + 6) % 7] = true;
        }
        int maxRun = 0;
        int run = 0;
        for (boolean carga : cargaNoDia) {
            run = carga ? run + 1 : 0;
            maxRun = Math.max(maxRun, run);
        }
        if (maxRun > n) {
            out.add(new ViolacaoQualidade(c.key(),
                    "Plano com " + maxRun + " dias de treino consecutivos (máximo permitido: " + n + ")"));
        }
    }

    private static TipoTreino parseTipo(String tipo) {
        if (tipo == null) return null;
        try {
            return TipoTreino.valueOf(tipo.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static DiaSemana parseDia(String dia) {
        if (dia == null) return null;
        try {
            return DiaSemana.valueOf(dia.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Extrai o pace mais rápido (menor m:ss) de um ritmoAlvo como "5:30/km" ou "5:00-5:15/km". */
    private static Optional<BigDecimal> parsePaceMaisRapido(String ritmoAlvo) {
        if (ritmoAlvo == null || ritmoAlvo.isBlank() || ritmoAlvo.length() > 50) return Optional.empty();
        Matcher m = PACE_TOKEN.matcher(ritmoAlvo);
        BigDecimal maisRapido = null;
        while (m.find()) {
            int min = Integer.parseInt(m.group(1));
            int seg = Integer.parseInt(m.group(2));
            BigDecimal decimal = BigDecimal.valueOf(min).add(BigDecimal.valueOf(seg).divide(BigDecimal.valueOf(60), 4, java.math.RoundingMode.HALF_UP));
            if (maisRapido == null || decimal.compareTo(maisRapido) < 0) {
                maisRapido = decimal;
            }
        }
        return Optional.ofNullable(maisRapido);
    }
}
