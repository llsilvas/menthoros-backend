package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.workout.HrTarget;
import br.com.menthoros.backend.domain.workout.PaceTarget;
import br.com.menthoros.backend.domain.workout.StructuredWorkout;
import br.com.menthoros.backend.dto.output.TreinoHojeDto;
import br.com.menthoros.backend.dto.output.TreinoHojeDto.AlvoPrimario;
import br.com.menthoros.backend.dto.output.TreinoHojeDto.EtapaAlvoDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.EtapaTreino;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.TipoTreino;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O alvo que a tela do atleta mostra tem de ser o mesmo que o relógio vai controlar: os casos
 * comparam contra o {@link IntervalsIcuWorkoutConverter}, não contra o parse dos campos brutos.
 */
@DisplayName("EtapaAlvoResolver")
class EtapaAlvoResolverTest {

    private static final int FC_LIMIAR = 170;

    private final IntervalsIcuFcAlvoResolver fcResolver = new IntervalsIcuFcAlvoResolver(new ZonaTreinoService());
    private final IntervalsIcuWorkoutConverter converter = new IntervalsIcuWorkoutConverter(fcResolver);
    private final EtapaAlvoResolver resolver = new EtapaAlvoResolver(fcResolver);

    private final Atleta comLimiar = Atleta.builder().fcLimiar(FC_LIMIAR).build();
    private final Atleta semLimiar = Atleta.builder().fcLimiar(null).build();

    @Test
    @DisplayName("FC e pace na mesma etapa: FC é o alvo (igual ao push), pace vira texto secundário")
    void fcVenceEPaceDesceParaTexto() {
        EtapaTreino etapa = etapa(1, "85-89%", "5:30-5:45");

        EtapaAlvoDto dto = resolver.resolver(etapa, comLimiar);

        HrTarget doPush = (HrTarget) stepDoConverter(etapa, comLimiar).meta();
        assertThat(dto.alvoPrimario()).isEqualTo(AlvoPrimario.FC);
        assertThat(dto.fcAlvoMin()).isEqualTo(doPush.startBpm());
        assertThat(dto.fcAlvoMax()).isEqualTo(doPush.endBpm());
        assertThat(dto.paceAlvo()).isNull();
        assertThat(dto.textoSecundario()).isEqualTo("5:30-5:45");
    }

    @Test
    @DisplayName("FC descartada por falta de limiar: pace assume e a FC prescrita vai para o texto")
    void fcDescartadaPaceAssume() {
        EtapaTreino etapa = etapa(1, "85-90%", "5:00");

        EtapaAlvoDto dto = resolver.resolver(etapa, semLimiar);

        PaceTarget doPush = (PaceTarget) stepDoConverter(etapa, semLimiar).meta();
        assertThat(dto.alvoPrimario()).isEqualTo(AlvoPrimario.PACE);
        assertThat(dto.paceAlvo()).isEqualTo("5:00");
        assertThat(doPush.startSecsPerKm()).isEqualTo(300);
        assertThat(dto.fcAlvoMin()).isNull();
        assertThat(dto.fcAlvoMax()).isNull();
        assertThat(dto.textoSecundario()).isEqualTo("85-90%");
    }

    @Test
    @DisplayName("faixa de pace é formatada m:ss-m:ss")
    void faixaDePace() {
        EtapaAlvoDto dto = resolver.resolver(etapa(1, null, "4:30-4:45"), comLimiar);

        assertThat(dto.alvoPrimario()).isEqualTo(AlvoPrimario.PACE);
        assertThat(dto.paceAlvo()).isEqualTo("4:30-4:45");
        assertThat(dto.textoSecundario()).isNull();
    }

    @Test
    @DisplayName("FC em bpm absoluto não depende do limiar")
    void bpmAbsoluto() {
        EtapaAlvoDto dto = resolver.resolver(etapa(1, "140-150 bpm", null), semLimiar);

        assertThat(dto.alvoPrimario()).isEqualTo(AlvoPrimario.FC);
        assertThat(dto.fcAlvoMin()).isEqualTo(140);
        assertThat(dto.fcAlvoMax()).isEqualTo(150);
    }

    @Test
    @DisplayName("sem alvo confiável: NENHUM, sem campos de FC/pace — não se inventa faixa")
    void semAlvo() {
        EtapaAlvoDto dto = resolver.resolver(etapa(1, "85-90%", null), semLimiar);

        assertThat(dto.alvoPrimario()).isEqualTo(AlvoPrimario.NENHUM);
        assertThat(dto.fcAlvoMin()).isNull();
        assertThat(dto.paceAlvo()).isNull();
        assertThat(dto.textoSecundario()).isEqualTo("85-90%");
    }

    @Test
    @DisplayName("campos descritivos da etapa atravessam (ordem, tipo, descrição, duração, distância, bloco)")
    void camposDescritivos() {
        EtapaTreino etapa = etapa(3, null, null);
        etapa.setDescricaoEtapa("Trote");
        etapa.setTipoEtapa("RECUPERACAO");

        EtapaAlvoDto dto = resolver.resolver(etapa, comLimiar);

        assertThat(dto.ordem()).isEqualTo(3);
        assertThat(dto.tipoEtapa()).isEqualTo("RECUPERACAO");
        assertThat(dto.descricao()).isEqualTo("Trote");
        assertThat(dto.duracaoMin()).isEqualTo(5);
        assertThat(dto.alvoPrimario()).isEqualTo(AlvoPrimario.NENHUM);
    }

    private static EtapaTreino etapa(int ordem, String fcAlvo, String ritmoAlvo) {
        return EtapaTreino.builder()
                .ordem(ordem)
                .tipoEtapa("INTERVALADO")
                .descricaoEtapa("Tiro")
                .duracaoMin(5)
                .fcAlvoEtapa(fcAlvo)
                .ritmoAlvo(ritmoAlvo)
                .build();
    }

    /** Um treino de uma etapa só, sem bloco, para comparar o step efetivo do push. */
    private StructuredWorkoutStep stepDoConverter(EtapaTreino etapa, Atleta atleta) {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setId(java.util.UUID.randomUUID());
        treino.setTipoTreino(TipoTreino.INTERVALADO);
        treino.setDataTreino(LocalDate.of(2026, 8, 27));
        treino.setDuracaoMin(Duration.ZERO);
        treino.setAtleta(atleta);
        treino.setEtapas(List.of(etapa));
        StructuredWorkout workout = converter.converter(treino).orElseThrow().workout();
        return new StructuredWorkoutStep(workout.steps().get(0).meta());
    }

    private record StructuredWorkoutStep(br.com.menthoros.backend.domain.workout.IntensityTarget meta) {}
}
