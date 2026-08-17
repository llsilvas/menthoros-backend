package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.workout.HrTarget;
import br.com.menthoros.backend.domain.workout.PaceTarget;
import br.com.menthoros.backend.domain.workout.StructuredWorkout;
import br.com.menthoros.backend.domain.workout.WorkoutStep;
import br.com.menthoros.backend.entity.EtapaTreino;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.TipoTreino;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Converte um {@link TreinoPlanejado} persistido no modelo canônico {@link StructuredWorkout}.
 * <p>
 * Responsabilidades: des-expansão de blocos (o banco persiste blocos JÁ expandidos — N cópias
 * físicas com o mesmo {@code blocoId} e {@code blocoRepeticoes=N}), aplicação da precedência
 * pace &gt; FC e normalização de dados degenerados sem nunca lançar exceção.
 * <p>
 * Classe pura, sem estado, sem dependência de contexto transacional — não deve receber uma
 * entidade fora de uma transação ativa quando {@code etapas} for lazy (o chamador é responsável
 * por garantir a inicialização da coleção antes de invocar {@link #converter(TreinoPlanejado)}).
 */
@Component
public class IntervalsIcuWorkoutConverter {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM");
    private static final BigDecimal METROS_POR_KM = BigDecimal.valueOf(1000);

    /**
     * Converte o treino planejado para o modelo canônico de treino estruturado.
     *
     * <p><b>Idempotente:</b> YES — leitura pura, não altera o treino.
     * <p><b>Side Effects:</b> NONE.
     * <p><b>Tenant-aware:</b> NO — não acessa {@code TenantContext}; o chamador já resolveu o
     * treino dentro do escopo do tenant correto.
     *
     * @param treino treino planejado a converter
     * @return o treino estruturado, ou {@link Optional#empty()} se o treino não for exportável
     *         (tipo DESCANSO, ou sem nenhum conteúdo prescritivo)
     */
    public Optional<StructuredWorkout> converter(TreinoPlanejado treino) {
        if (treino == null) {
            throw new IllegalArgumentException("TreinoPlanejado não pode ser nulo");
        }
        if (treino.getTipoTreino() == TipoTreino.DESCANSO) {
            return Optional.empty();
        }

        // Etapas sem nenhuma duração/distância positiva não são prescritivas: cai no mesmo
        // caminho do treino sem etapas (step único com duração/distância do próprio treino).
        List<EtapaTreino> etapas = etapasValidas(treino);
        List<WorkoutStep> steps = temEtapaPrescritiva(etapas) ? desExpandir(etapas) : stepUnico(treino);
        if (steps.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new StructuredWorkout(
                StructuredWorkout.externalIdCanonico(treino.getId()),
                nome(treino),
                null,
                treino.getDataTreino(),
                treino.getDescricao(),
                steps));
    }

    // ===== Exportabilidade =====

    private boolean temEtapaPrescritiva(List<EtapaTreino> etapas) {
        return etapas.stream().anyMatch(this::etapaComDuracaoOuDistanciaPositiva);
    }

    private boolean etapaComDuracaoOuDistanciaPositiva(EtapaTreino etapa) {
        return (etapa.getDuracaoMin() != null && etapa.getDuracaoMin() > 0)
                || distanciaPositiva(etapa.getDistanciaKm());
    }

    // ===== Seleção e ordenação de etapas =====

    private List<EtapaTreino> etapasValidas(TreinoPlanejado treino) {
        List<EtapaTreino> etapas = treino.getEtapas();
        if (etapas == null) {
            return List.of();
        }
        return etapas.stream()
                .filter(Objects::nonNull)
                .filter(this::etapaComConteudo)
                .sorted(Comparator.comparing(EtapaTreino::getOrdem, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private boolean etapaComConteudo(EtapaTreino etapa) {
        return isPresente(etapa.getTipoEtapa())
                || isPresente(etapa.getDescricaoEtapa())
                || etapa.getDuracaoMin() != null
                || etapa.getDistanciaKm() != null
                || isPresente(etapa.getFcAlvoEtapa())
                || isPresente(etapa.getRitmoAlvo());
    }

    // ===== Treino sem etapas =====

    private List<WorkoutStep> stepUnico(TreinoPlanejado treino) {
        Integer distanceMeters = distanciaEmMetros(treino.getDistanciaKm());
        Integer durationSeconds = distanceMeters != null ? null : duracaoEmSegundos(treino.getDuracaoMin());
        if (distanceMeters == null && durationSeconds == null) {
            return List.of();
        }
        HrTarget hr = IntervalsIcuTargetParser.parseZona(treino.getZonaAlvo()).orElse(null);
        return List.of(WorkoutStep.simples(null, durationSeconds, distanceMeters, null, hr));
    }

    // ===== Des-expansão de blocos =====

    private List<WorkoutStep> desExpandir(List<EtapaTreino> etapas) {
        List<WorkoutStep> resultado = new ArrayList<>();
        int i = 0;
        int total = etapas.size();
        while (i < total) {
            UUID blocoId = etapas.get(i).getBlocoId();
            if (blocoId == null) {
                BlocoInferido inferido = inferirBloco(etapas, i);
                resultado.add(inferido.step());
                i += inferido.etapasConsumidas();
                continue;
            }

            int fim = i + 1;
            while (fim < total && blocoId.equals(etapas.get(fim).getBlocoId())) {
                fim++;
            }
            List<EtapaTreino> grupo = etapas.subList(i, fim);

            WorkoutStep bloco = tentarMontarBloco(grupo);
            if (bloco != null) {
                resultado.add(bloco);
            } else {
                grupo.forEach(etapa -> resultado.add(stepDeEtapa(etapa)));
            }
            i = fim;
        }
        return resultado;
    }

    /**
     * Reconstrói um bloco a partir da repetição observada, para etapas <b>sem</b> {@code blocoId}.
     *
     * <p>O caminho da IA nunca atribui {@code blocoId} — só o do treinador, via
     * {@code tipoEtapa=BLOCO}. Sem esta inferência, um fartlek "4x (1min forte + 2min leve)" chega
     * ao relógio como 8 steps sequenciais, e a estrutura da série se perde.</p>
     *
     * <p>Escolhe a janela que cobre mais etapas; empatada a cobertura, prefere mais repetições
     * (série mais compacta). Sem repetição de pelo menos duas janelas, emite a etapa isolada — o
     * bloco nunca é inventado a partir de etapas heterogêneas.</p>
     *
     * <p><b>A janela precisa conter uma etapa {@code INTERVALADO}</b>, que é o que caracteriza uma
     * série no domínio. Sem essa exigência, repetição vira sinônimo de série e a inferência inventa
     * estrutura: um ondulado "10min Z2 / 5min Z3 / 10min Z2 / 5min Z3" viraria
     * "2x (10min Z2 + 5min Z3)" — um bloco que o treinador não prescreveu.</p>
     *
     * @return o step montado e quantas etapas ele consumiu, nunca menos que 1
     */
    private BlocoInferido inferirBloco(List<EtapaTreino> etapas, int inicio) {
        int limite = inicio;
        while (limite < etapas.size() && etapas.get(limite).getBlocoId() == null) {
            limite++;
        }
        int disponivel = limite - inicio;

        int melhorJanela = 0;
        int melhorReps = 0;
        for (int janela = 1; janela <= disponivel / 2; janela++) {
            if (!contemIntervalado(etapas, inicio, janela)) {
                continue;
            }
            int reps = contarRepeticoes(etapas, inicio, janela, limite);
            if (reps < 2) {
                continue;
            }
            int cobertura = janela * reps;
            int melhorCobertura = melhorJanela * melhorReps;
            if (cobertura > melhorCobertura || (cobertura == melhorCobertura && reps > melhorReps)) {
                melhorJanela = janela;
                melhorReps = reps;
            }
        }

        if (melhorReps < 2) {
            return new BlocoInferido(stepDeEtapa(etapas.get(inicio)), 1);
        }

        List<WorkoutStep> subSteps = etapas.subList(inicio, inicio + melhorJanela).stream()
                .map(this::stepDeEtapa)
                .toList();
        return new BlocoInferido(WorkoutStep.bloco(null, melhorReps, subSteps),
                melhorJanela * melhorReps);
    }

    /** Resultado de {@link #inferirBloco}: o step montado e quantas etapas ele consumiu. */
    private record BlocoInferido(WorkoutStep step, int etapasConsumidas) {}

    /** Uma série tem esforço: sem etapa INTERVALADO, a repetição é coincidência, não bloco. */
    private boolean contemIntervalado(List<EtapaTreino> etapas, int inicio, int janela) {
        for (int pos = inicio; pos < inicio + janela; pos++) {
            if ("INTERVALADO".equalsIgnoreCase(etapas.get(pos).getTipoEtapa())) {
                return true;
            }
        }
        return false;
    }

    /** Quantas vezes a janela iniciada em {@code inicio} se repete consecutivamente até {@code limite}. */
    private int contarRepeticoes(List<EtapaTreino> etapas, int inicio, int janela, int limite) {
        int reps = 1;
        int proxima = inicio + janela;
        while (proxima + janela <= limite && janelaEquivalente(etapas, inicio, proxima, janela)) {
            reps++;
            proxima += janela;
        }
        return reps;
    }

    private boolean janelaEquivalente(List<EtapaTreino> etapas, int a, int b, int janela) {
        for (int pos = 0; pos < janela; pos++) {
            if (!etapasEquivalentes(etapas.get(a + pos), etapas.get(b + pos))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tenta montar um bloco {@code reps=N} a partir de um grupo consecutivo com o mesmo
     * {@code blocoId}. Retorna {@code null} quando o grupo não pode ser dividido em N janelas
     * idênticas — nesse caso o chamador aplica o fallback seguro (steps individuais expandidos).
     */
    private WorkoutStep tentarMontarBloco(List<EtapaTreino> grupo) {
        Integer reps = grupo.get(0).getBlocoRepeticoes();
        if (reps == null || reps <= 1 || grupo.size() % reps != 0) {
            return null;
        }
        List<List<EtapaTreino>> janelas = dividirEmJanelas(grupo, reps);
        if (!janelasIdenticas(janelas)) {
            return null;
        }
        List<WorkoutStep> subSteps = janelas.get(0).stream().map(this::stepDeEtapa).toList();
        return WorkoutStep.bloco(null, reps, subSteps);
    }

    private List<List<EtapaTreino>> dividirEmJanelas(List<EtapaTreino> grupo, int reps) {
        int tamanhoJanela = grupo.size() / reps;
        List<List<EtapaTreino>> janelas = new ArrayList<>(reps);
        for (int r = 0; r < reps; r++) {
            janelas.add(grupo.subList(r * tamanhoJanela, (r + 1) * tamanhoJanela));
        }
        return janelas;
    }

    private boolean janelasIdenticas(List<List<EtapaTreino>> janelas) {
        List<EtapaTreino> referencia = janelas.get(0);
        for (int idx = 1; idx < janelas.size(); idx++) {
            List<EtapaTreino> atual = janelas.get(idx);
            if (atual.size() != referencia.size()) {
                return false;
            }
            for (int pos = 0; pos < referencia.size(); pos++) {
                if (!etapasEquivalentes(referencia.get(pos), atual.get(pos))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean etapasEquivalentes(EtapaTreino a, EtapaTreino b) {
        return Objects.equals(a.getTipoEtapa(), b.getTipoEtapa())
                && Objects.equals(a.getDuracaoMin(), b.getDuracaoMin())
                && distanciasIguais(a.getDistanciaKm(), b.getDistanciaKm())
                && Objects.equals(a.getRitmoAlvo(), b.getRitmoAlvo())
                && Objects.equals(a.getFcAlvoEtapa(), b.getFcAlvoEtapa());
    }

    private boolean distanciasIguais(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }

    // ===== Mapeamento de uma etapa =====

    private WorkoutStep stepDeEtapa(EtapaTreino etapa) {
        Integer distanceMeters = distanciaEmMetros(etapa.getDistanciaKm());
        Integer durationSeconds = distanceMeters != null ? null : duracaoEmSegundos(etapa.getDuracaoMin());

        String text = normalizaTexto(etapa.getDescricaoEtapa());
        PaceTarget pace = IntervalsIcuTargetParser.parsePace(etapa.getRitmoAlvo()).orElse(null);
        HrTarget hr = null;

        if (pace != null) {
            if (isPresente(etapa.getFcAlvoEtapa())) {
                text = anexarFc(text, etapa.getFcAlvoEtapa());
            }
        } else {
            hr = IntervalsIcuTargetParser.parseFc(etapa.getFcAlvoEtapa()).orElse(null);
        }

        return WorkoutStep.simples(text, durationSeconds, distanceMeters, pace, hr);
    }

    private String anexarFc(String textoBase, String fcAlvoEtapa) {
        String sufixo = "(" + fcAlvoEtapa.trim() + ")";
        return textoBase == null ? sufixo : textoBase + " " + sufixo;
    }

    // ===== Normalização numérica =====

    private Integer duracaoEmSegundos(Integer minutos) {
        if (minutos == null || minutos <= 0) {
            return null;
        }
        return minutos * 60;
    }

    private Integer duracaoEmSegundos(Duration duracao) {
        if (duracao == null) {
            return null;
        }
        long segundos = duracao.toSeconds();
        return segundos > 0 ? (int) segundos : null;
    }

    private Integer distanciaEmMetros(BigDecimal distanciaKm) {
        if (!distanciaPositiva(distanciaKm)) {
            return null;
        }
        try {
            return distanciaKm.multiply(METROS_POR_KM).setScale(0, RoundingMode.HALF_UP).intValueExact();
        } catch (ArithmeticException e) {
            // valor fora do range de int é dado degenerado, não incidente: step fica sem distance
            return null;
        }
    }

    private boolean distanciaPositiva(BigDecimal distanciaKm) {
        return distanciaKm != null && distanciaKm.signum() > 0;
    }

    private boolean isPresente(String valor) {
        return valor != null && !valor.isBlank();
    }

    private String normalizaTexto(String valor) {
        return isPresente(valor) ? valor.trim() : null;
    }

    // ===== Nome do evento =====

    // Distância no nome (decisão do founder no walking skeleton): "12 Km - LONGO" é mais útil
    // no relógio que a data, que o calendário já mostra. Sem distância, cai no formato com data.
    private String nome(TreinoPlanejado treino) {
        String tipo = treino.getTipoTreino() != null ? treino.getTipoTreino().name() : "TREINO";
        if (treino.getDistanciaKm() != null && treino.getDistanciaKm().compareTo(BigDecimal.ZERO) > 0) {
            return treino.getDistanciaKm().stripTrailingZeros().toPlainString() + " Km - " + tipo;
        }
        String data = treino.getDataTreino() != null ? treino.getDataTreino().format(FORMATO_DATA) : "";
        return (tipo + " " + data).trim();
    }
}
