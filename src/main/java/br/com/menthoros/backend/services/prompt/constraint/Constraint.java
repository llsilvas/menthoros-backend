package br.com.menthoros.backend.services.prompt.constraint;

import br.com.menthoros.backend.enums.CategoriaIntervalado;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.TipoTreino;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Regra determinística que o plano de treino deve respeitar, na forma declarativa única do seam:
 * <ul>
 *   <li>{@code key} — dirige a verificação (dispatch no {@code PlanQualityChecker});</li>
 *   <li>{@code descricao} — alimenta o bloco mandatório no topo do prompt;</li>
 *   <li>{@code params} — dados serializáveis lidos pela verificação (ver {@link ConstraintKey}).</li>
 * </ul>
 *
 * <p>Quem produz a {@code Constraint} (formatter hoje, skill amanhã) pode trocar sem que o renderer
 * ou o checker mudem. Serializável (records + Jackson) para compatibilidade com persistência de skills.
 */
public record Constraint(ConstraintKey key, String descricao, Map<String, Object> params) {

    /** Chave de params: {@code Map<String(TipoTreino), BigDecimal>} (pace em minutos decimais). */
    static final String PARAM_TETO = "teto";
    /** Chave de params: {@code List<String(DiaSemana)>}. */
    static final String PARAM_DIAS = "dias";
    /** Chave de params: {@code Integer} (máx. de dias consecutivos de treino). */
    static final String PARAM_N = "n";
    /** Chave de params: {@code String} (nome da {@link CategoriaIntervalado} segura). */
    static final String PARAM_CATEGORIA_SEGURA = "categoriaSegura";

    public Constraint {
        if (key == null) {
            throw new IllegalArgumentException("Constraint.key não pode ser nula");
        }
        if (descricao == null || descricao.isBlank()) {
            throw new IllegalArgumentException("Constraint.descricao não pode ser vazia");
        }
        params = (params == null) ? Map.of() : Map.copyOf(params);
    }

    // ===== Factories (encapsulam o schema de params por key) =====

    /** Plano NÃO pode conter INTERVALADO (decisão Substituído). */
    public static Constraint intervaladoProibido(String descricao) {
        return new Constraint(ConstraintKey.INTERVALADO_PROIBIDO, descricao, Map.of());
    }

    /** Intervalado limitado a uma categoria segura (decisão Degradado) — declarada, verificação adiada. */
    public static Constraint intervaladoMaxCategoria(String descricao, CategoriaIntervalado categoriaSegura) {
        return new Constraint(ConstraintKey.INTERVALADO_MAX_CATEGORIA, descricao,
                Map.of(PARAM_CATEGORIA_SEGURA, categoriaSegura.name()));
    }

    /** Teto de pace por tipo: nenhuma etapa pode ser mais rápida que o teto do seu tipo. */
    public static Constraint paceTeto(String descricao, Map<TipoTreino, BigDecimal> tetoPorTipo) {
        Map<String, Object> teto = new LinkedHashMap<>();
        tetoPorTipo.forEach((tipo, pace) -> teto.put(tipo.name(), pace));
        return new Constraint(ConstraintKey.PACE_TETO, descricao, Map.of(PARAM_TETO, teto));
    }

    /** Treinos só podem cair nos dias permitidos. */
    public static Constraint diasPermitidos(String descricao, List<DiaSemana> dias) {
        return new Constraint(ConstraintKey.DIAS_PERMITIDOS, descricao,
                Map.of(PARAM_DIAS, dias.stream().map(Enum::name).toList()));
    }

    /** No máximo {@code n} dias de treino consecutivos na semana. */
    public static Constraint maxConsecutivos(String descricao, int n) {
        return new Constraint(ConstraintKey.MAX_CONSECUTIVOS, descricao, Map.of(PARAM_N, n));
    }

    /** FC limiar estimado por inferência dos últimos 30 dias. */
    public static Constraint limiarFcEstimado(String descricao) {
        return new Constraint(ConstraintKey.LIMIAR_FC_ESTIMADO, descricao, Map.of());
    }

    /** Pace limiar estimado por inferência dos últimos 30 dias. */
    public static Constraint limiarPaceEstimado(String descricao) {
        return new Constraint(ConstraintKey.LIMIAR_PACE_ESTIMADO, descricao, Map.of());
    }

    // ===== Accessors tipados (lidos pelo checker; tolerantes a params ausente) =====

    /** Teto de pace por tipo (vazio se não aplicável). */
    public Map<TipoTreino, BigDecimal> tetoPorTipo() {
        Object raw = params.get(PARAM_TETO);
        if (!(raw instanceof Map<?, ?> mapa)) {
            return Map.of();
        }
        Map<TipoTreino, BigDecimal> out = new EnumMap<>(TipoTreino.class);
        mapa.forEach((tipo, pace) -> out.put(TipoTreino.valueOf(tipo.toString()), toBigDecimal(pace)));
        return out;
    }

    /** Dias permitidos (vazio se não aplicável). */
    public List<DiaSemana> diasPermitidos() {
        Object raw = params.get(PARAM_DIAS);
        if (!(raw instanceof List<?> lista)) {
            return List.of();
        }
        return lista.stream().map(Object::toString).map(DiaSemana::valueOf).toList();
    }

    /** Máximo de dias consecutivos, ou {@code null} se não aplicável. */
    public Integer maxConsecutivos() {
        Object raw = params.get(PARAM_N);
        return (raw instanceof Number n) ? n.intValue() : null;
    }

    /** Categoria segura ({@code INTERVALADO_MAX_CATEGORIA}), ou {@code null} se não aplicável. */
    public CategoriaIntervalado categoriaSegura() {
        Object raw = params.get(PARAM_CATEGORIA_SEGURA);
        return (raw instanceof String s) ? CategoriaIntervalado.valueOf(s) : null;
    }

    private static BigDecimal toBigDecimal(Object value) {
        return switch (value) {
            case BigDecimal b -> b;
            case Number n -> BigDecimal.valueOf(n.doubleValue());
            case String s -> new BigDecimal(s);
            case null, default -> throw new IllegalArgumentException("Valor de pace inválido em params: " + value);
        };
    }
}
