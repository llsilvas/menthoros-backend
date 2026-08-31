package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.input.EtapaRealizadaInputDto;
import br.com.menthoros.backend.dto.input.TreinoPlanejadoInputDto;
import br.com.menthoros.backend.dto.input.TreinoRealizadoInputDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.dto.output.EtapaRealizadaOutputDto;
import br.com.menthoros.backend.dto.output.EtapaTreinoDto;
import br.com.menthoros.backend.dto.output.RunningDynamicsOutputDto;
import br.com.menthoros.backend.dto.output.TreinoPlanejadoOutputDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.EtapaTreino;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.services.helper.DecouplingCalculatorService;
import br.com.menthoros.backend.services.helper.LapEfficiencySeriesCalculator;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(
        componentModel = "spring",
        unmappedSourcePolicy = ReportingPolicy.IGNORE,
        uses = {DecouplingCalculatorService.class, LapEfficiencySeriesCalculator.class}
)
public interface TreinoMapper {

    // ===== Conversores de Duration <-> String (HH:MM:SS ou MM:SS) =====

    @Named("stringToDuration")
    default Duration stringToDuration(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }

        String[] parts = time.split(":");

        try {
            if (parts.length == 3) {
                // Formato HH:MM:SS
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                int seconds = Integer.parseInt(parts[2]);
                return Duration.ofHours(hours).plusMinutes(minutes).plusSeconds(seconds);
            } else if (parts.length == 2) {
                // Formato MM:SS
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1]);
                return Duration.ofMinutes(minutes).plusSeconds(seconds);
            } else if (parts.length == 1) {
                // Apenas minutos
                int minutes = Integer.parseInt(parts[0]);
                return Duration.ofMinutes(minutes);
            }
        } catch (NumberFormatException e) {
            return null;
        }

        return null;
    }

    @Named("durationToString")
    default String durationToString(Duration duration) {
        if (duration == null) {
            return null;
        }

        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    // ===== Conversores de BigDecimal <-> Double =====

    @Named("doubleToBigDecimal")
    default BigDecimal doubleToBigDecimal(Double value) {
        return value != null ? BigDecimal.valueOf(value) : null;
    }

    @Named("bigDecimalToDouble")
    default Double bigDecimalToDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    // ===== Running dynamics (import .fit) — escalares simples agrupados num sub-record,
    // mesmo padrão de DecouplingResultadoDto/LapEfficiencySeriesDto (fluxo comum, design D3
    // de fit-running-dynamics-ingestion) =====

    @Named("runningDynamicsDeTreino")
    default RunningDynamicsOutputDto runningDynamicsDeTreino(TreinoRealizado treino) {
        if (treino == null) {
            return null;
        }
        return new RunningDynamicsOutputDto(
                durationToString(treino.getTempoMovimento()),
                treino.getCalorias(),
                treino.getGctMedioMs(),
                bigDecimalToDouble(treino.getGctEquilibrioPct()),
                bigDecimalToDouble(treino.getPassadaMediaM()),
                bigDecimalToDouble(treino.getOscilacaoVerticalCm()),
                bigDecimalToDouble(treino.getProporcaoVerticalPct()),
                bigDecimalToDouble(treino.getTemperaturaMediaC())
        );
    }

    /** Mesmos campos de {@link #runningDynamicsDeTreino}, exceto calorias — só existe a nível de sessão. */
    @Named("runningDynamicsDeEtapa")
    default RunningDynamicsOutputDto runningDynamicsDeEtapa(EtapaRealizada etapa) {
        if (etapa == null) {
            return null;
        }
        return new RunningDynamicsOutputDto(
                durationToString(etapa.getTempoMovimento()),
                null,
                etapa.getGctMedioMs(),
                bigDecimalToDouble(etapa.getGctEquilibrioPct()),
                bigDecimalToDouble(etapa.getPassadaMediaM()),
                bigDecimalToDouble(etapa.getOscilacaoVerticalCm()),
                bigDecimalToDouble(etapa.getProporcaoVerticalPct()),
                bigDecimalToDouble(etapa.getTemperaturaMediaC())
        );
    }

    @Mapping(target = "planoSemanal.id", source = "planoSemanalId")
    @Mapping(target = "atleta.id", source = "atletaId")
    @Mapping(target = "duracaoMin", source = "duracaoMin", qualifiedByName = "stringToDuration")
    @Mapping(target = "distanciaKm", source = "distanciaKm", qualifiedByName = "doubleToBigDecimal")
    TreinoPlanejado toEntity(TreinoPlanejadoInputDto dto);

    @Mapping(target = "duracaoMin", source = "duracaoMin", qualifiedByName = "durationToString")
    @Mapping(target = "distanciaKm", source = "distanciaKm", qualifiedByName = "bigDecimalToDouble")
    @Mapping(target = "treinoRealizadoId", expression = "java(safeGetTreinoRealizadoId(treinoPlanejado))")
    @Mapping(target = "percepcaoEsforcoRealizado", expression = "java(safeGetPercepcaoEsforcoRealizado(treinoPlanejado))")
    @Mapping(target = "etapas", expression = "java(safeGetEtapas(treinoPlanejado))")
    // analiseAtletaDisponivel é enriquecido no PlanoServiceImpl (consulta às análises), não aqui.
    @Mapping(target = "analiseAtletaDisponivel", ignore = true)
    TreinoPlanejadoOutputDto toOutputDto(TreinoPlanejado treinoPlanejado);

    // Hibernate.isInitialized é insuficiente para @OneToOne(mappedBy=...) fora de sessão.
    // try-catch garante retorno null sem explodir quando a sessão já fechou.
    default UUID safeGetTreinoRealizadoId(TreinoPlanejado tp) {
        try {
            TreinoRealizado tr = tp.getTreinoRealizado();
            return tr != null ? tr.getId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    default Integer safeGetPercepcaoEsforcoRealizado(TreinoPlanejado tp) {
        try {
            TreinoRealizado tr = tp.getTreinoRealizado();
            return tr != null ? tr.getPercepcaoEsforco() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // Protege contra LazyInitializationException fora de transação
    default List<EtapaTreinoDto> safeGetEtapas(TreinoPlanejado tp) {
        try {
            List<EtapaTreino> etapas = tp.getEtapas();
            if (etapas == null) return null;
            return etapas.stream()
                    .map(e -> new EtapaTreinoDto(
                            e.getOrdem(), e.getTipoEtapa(), e.getDescricaoEtapa(),
                            e.getDuracaoMin(),
                            e.getDistanciaKm() != null ? e.getDistanciaKm().doubleValue() : null,
                            e.getFcAlvoEtapa(), e.getRitmoAlvo(), e.getRepeticoes(),
                            e.getBlocoId(), e.getBlocoRepeticoes()
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return null;
        }
    }

    // Adicionado para conversão direta de DTO de saída para entidade
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "planoSemanal", ignore = true)
    @Mapping(target = "atleta", ignore = true)
    @Mapping(target = "duracaoMin", source = "duracaoMin", qualifiedByName = "stringToDuration")
    @Mapping(target = "distanciaKm", source = "distanciaKm", qualifiedByName = "doubleToBigDecimal")
    TreinoPlanejado toEntity(TreinoPlanejadoLlmDto dto);

    @Named("treinoPlanejadoListToOutputDtoList")
    List<TreinoPlanejadoOutputDto> toOutputDtoListTreinoPlanejado(List<TreinoPlanejado> treinosPlanejados);

    @Mapping(target = "atleta.id", source = "atletaId")
    @Mapping(target = "planoSemanal", ignore = true)
    @Mapping(target = "treinoPlanejado.id", source = "treinoPlanejadoId")
    @Mapping(target = "treinoPlanejadoId", ignore = true)
    @Mapping(target = "duracaoMin", source = "duracaoMin", qualifiedByName = "stringToDuration")
    @Mapping(target = "paceMedia", source = "ritmoMedio", qualifiedByName = "stringToDuration")
    @Mapping(target = "distanciaKm", source = "distanciaKm", qualifiedByName = "doubleToBigDecimal")
    TreinoRealizado toEntity(TreinoRealizadoInputDto dto);

    @Mapping(target = "duracaoMin", source = "duracaoMin", qualifiedByName = "durationToString")
    @Mapping(target = "paceMedia", source = "paceMedia", qualifiedByName = "durationToString")
    @Mapping(target = "distanciaKm", source = "distanciaKm", qualifiedByName = "bigDecimalToDouble")
    @Mapping(target = "sugestaoReclassificacao", ignore = true)
    @Mapping(target = "runningDynamics", source = ".", qualifiedByName = "runningDynamicsDeTreino")
    @Mapping(target = "decouplingPercentual", source = ".", qualifiedByName = "decouplingDeTreino")
    // Envelope completo (Pw:HR/potência inclusos) e série de EF ficam fora do fluxo comum
    // (listagens): só o detalhe os carrega (design D4). O legado decouplingPercentual continua
    // aqui para compatibilidade (CA4).
    @Mapping(target = "decoupling", ignore = true)
    @Mapping(target = "serieEficiencia", ignore = true)
    @Named("treinoRealizadoToOutputDto")
    TreinoRealizadoOutputDto toOutputDto(TreinoRealizado treinoRealizado);

    /**
     * Variante de DETALHE ({@code GET /api/v1/treinos/realizados/{id}}): herda o mapeamento comum
     * e adiciona o envelope completo de decoupling (Pa:HR + Pw:HR) e a série de EF por volta —
     * payload maior, deliberadamente fora das listagens.
     */
    @InheritConfiguration(name = "toOutputDto")
    @Mapping(target = "decoupling", source = ".", qualifiedByName = "decouplingResultadoDeTreino")
    @Mapping(target = "serieEficiencia", source = ".", qualifiedByName = "serieEficienciaDeTreino")
    TreinoRealizadoOutputDto toOutputDtoDetalhado(TreinoRealizado treinoRealizado);

    // ===== EtapaRealizada: Input -> Entity =====

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "treinoRealizado", ignore = true)
    @Mapping(target = "etapaPlanejada", ignore = true)
    @Mapping(target = "duracao", source = "duracao", qualifiedByName = "stringToDuration")
    @Mapping(target = "paceMedia", source = "paceMedia", qualifiedByName = "stringToDuration")
    @Mapping(target = "distanciaKm", source = "distanciaKm", qualifiedByName = "doubleToBigDecimal")
    @Mapping(target = "velocidadeMedia", source = "velocidadeMedia", qualifiedByName = "doubleToBigDecimal")
    EtapaRealizada toEntity(EtapaRealizadaInputDto dto);

    List<EtapaRealizada> toEtapaRealizadaEntityList(List<EtapaRealizadaInputDto> dtos);

    // ===== EtapaRealizada: Entity -> Output =====

    @Mapping(target = "etapaPlanejadaId", source = "etapaPlanejada.id")
    @Mapping(target = "duracao", source = "duracao", qualifiedByName = "durationToString")
    @Mapping(target = "paceMedia", source = "paceMedia", qualifiedByName = "durationToString")
    @Mapping(target = "distanciaKm", source = "distanciaKm", qualifiedByName = "bigDecimalToDouble")
    @Mapping(target = "velocidadeMedia", source = "velocidadeMedia", qualifiedByName = "bigDecimalToDouble")
    @Mapping(target = "runningDynamics", source = ".", qualifiedByName = "runningDynamicsDeEtapa")
    EtapaRealizadaOutputDto toOutputDto(EtapaRealizada etapaRealizada);

    List<EtapaRealizadaOutputDto> toEtapaRealizadaOutputDtoList(List<EtapaRealizada> etapas);

    @Named("treinoRealizadoListToOutputDtoList")
    @IterableMapping(qualifiedByName = "treinoRealizadoToOutputDto")
    List<TreinoRealizadoOutputDto> toOutputDtoListTreinoRealizado(List<TreinoRealizado> treinosRealizados);

    @AfterMapping
    default void linkEtapas(@MappingTarget TreinoPlanejado treinoPlanejado){
        if(treinoPlanejado.getEtapas() != null){
            treinoPlanejado.getEtapas().forEach(etapa ->
                    etapa.setTreinoPlanejado(treinoPlanejado));
        }
    }

    @AfterMapping
    default void linkEtapasRealizadas(@MappingTarget TreinoRealizado treinoRealizado) {
        if (treinoRealizado.getEtapasRealizadas() != null) {
            treinoRealizado.getEtapasRealizadas().forEach(etapa ->
                    etapa.setTreinoRealizado(treinoRealizado));
        }
    }

}
