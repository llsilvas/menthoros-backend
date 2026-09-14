package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaFC;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code bpmDaZona} / {@code zonaParaFc} / {@code adicionarTiroERecuperacao} — helpers de zona de
 * FC usados na expansão e normalização de intervalado (refactor-iaservice-decomposition, seção 3).
 * P0: etapas auto-inseridas pelo normalizador devem produzir FC em formato "NNN-NNN bpm" que o
 * validador de FC (seção 4) aceita — não no formato legado "% FCmax".
 */
@DisplayName("TreinoNormalizador — helpers de zona de FC")
class TreinoNormalizadorZonaHelpersTest {

    private TreinoNormalizador normalizador;
    private List<ZonaFC> zonasFC160;

    @BeforeEach
    void setUp() {
        normalizador = new TreinoNormalizador();
        zonasFC160 = List.of(
                new ZonaFC(1, "Recuperação", 120, 136),
                new ZonaFC(2, "Aeróbico",    136, 142),
                new ZonaFC(3, "Tempo",       142, 150),
                new ZonaFC(4, "Limiar",      150, 160),
                new ZonaFC(5, "VO2max",      160, 170)
        );
    }

    @Test
    @DisplayName("bpmDaZona com zonas válidas → retorna 'fcMin-fcMax bpm'")
    void bpmDaZona_comZonas_retornaFormatoBpm() {
        assertThat(normalizador.bpmDaZona(zonasFC160, 4)).isEqualTo("160-170 bpm"); // Z5
    }

    @Test
    @DisplayName("bpmDaZona com zonas null → retorna null")
    void bpmDaZona_semZonas_retornaNull() {
        assertThat(normalizador.bpmDaZona(null, 4)).isNull();
    }

    @Test
    @DisplayName("bpmDaZona com índice fora do range → retorna null")
    void bpmDaZona_indiceForaRange_retornaNull() {
        assertThat(normalizador.bpmDaZona(zonasFC160, 10)).isNull();
    }

    @Test
    @DisplayName("zonaParaFc com zonas disponíveis → retorna bpm, não % FCmax")
    void zonaParaFc_comZonas_retornaBpm() {
        assertThat(normalizador.zonaParaFc("Z5", zonasFC160)).isEqualTo("160-170 bpm");
        assertThat(normalizador.zonaParaFc("Z4", zonasFC160)).isEqualTo("150-160 bpm");
        assertThat(normalizador.zonaParaFc("Z3", zonasFC160)).isEqualTo("142-150 bpm");
        assertThat(normalizador.zonaParaFc("Z2", zonasFC160)).isEqualTo("136-142 bpm");
        assertThat(normalizador.zonaParaFc("Z1", zonasFC160)).isEqualTo("120-136 bpm");
    }

    @Test
    @DisplayName("zonaParaFc sem zonas (null) → mantém fallback % FCmax para compatibilidade")
    void zonaParaFc_semZonas_retornaFallbackPercentual() {
        assertThat(normalizador.zonaParaFc("Z5", null)).isEqualTo("90-95% FCmax");
        assertThat(normalizador.zonaParaFc("Z1", null)).isEqualTo("60-70% FCmax");
    }

    @Test
    @DisplayName("adicionarTiroERecuperacao com zonas → FC em formato bpm que parseFcRange aceita")
    void adicionarTiroERecuperacao_comZonas_fcEmBpm() {
        List<EtapaTreinoLlmDto> etapasBase = List.of(
                new EtapaTreinoLlmDto(1, "AQUECIMENTO",    "Trote leve",   10, 1.5, "120-136 bpm", 1, null),
                new EtapaTreinoLlmDto(2, "INTERVALADO",    "Tiro 1",        4, 0.4, "160-170 bpm", 1, null),
                new EtapaTreinoLlmDto(3, "RECUPERACAO",    "Trote rec",     2, 0.2, "120-136 bpm", 1, null),
                new EtapaTreinoLlmDto(4, "DESAQUECIMENTO", "Caminhada",     8, 1.0, "120-136 bpm", 1, null)
        );

        List<EtapaTreinoLlmDto> resultado = normalizador.adicionarTiroERecuperacao(
                new java.util.ArrayList<>(etapasBase), 0.8, 0.3, 4, 2, zonasFC160);

        List<EtapaTreinoLlmDto> novas = resultado.stream()
                .filter(e -> e.descricaoEtapa().contains("extra"))
                .toList();

        assertThat(novas).hasSize(2);

        EtapaTreinoLlmDto tiroExtra = novas.stream()
                .filter(e -> "INTERVALADO".equals(e.tipoEtapa())).findFirst().orElseThrow();
        EtapaTreinoLlmDto recExtra = novas.stream()
                .filter(e -> "RECUPERACAO".equals(e.tipoEtapa())).findFirst().orElseThrow();

        assertThat(tiroExtra.fcAlvoEtapa()).matches("\\d{2,3}-\\d{2,3} bpm");
        assertThat(recExtra.fcAlvoEtapa()).matches("\\d{2,3}-\\d{2,3} bpm");

        assertThat(tiroExtra.fcAlvoEtapa()).isEqualTo("160-170 bpm"); // Z5
        assertThat(recExtra.fcAlvoEtapa()).isEqualTo("120-136 bpm");  // Z1
    }

    @Test
    @DisplayName("adicionarTiroERecuperacao sem zonas (null) → mantém fallback % FCmax")
    void adicionarTiroERecuperacao_semZonas_fcFallback() {
        List<EtapaTreinoLlmDto> etapasBase = List.of(
                new EtapaTreinoLlmDto(1, "AQUECIMENTO",    "Trote leve", 10, 1.5, "120-136 bpm", 1, null),
                new EtapaTreinoLlmDto(2, "DESAQUECIMENTO", "Caminhada",   8, 1.0, "120-136 bpm", 1, null)
        );

        List<EtapaTreinoLlmDto> resultado = normalizador.adicionarTiroERecuperacao(
                new java.util.ArrayList<>(etapasBase), 0.8, 0.3, 4, 2, null);

        List<EtapaTreinoLlmDto> novas = resultado.stream()
                .filter(e -> e.descricaoEtapa().contains("extra"))
                .toList();

        assertThat(novas).hasSize(2);
        EtapaTreinoLlmDto tiroExtra = novas.stream()
                .filter(e -> "INTERVALADO".equals(e.tipoEtapa())).findFirst().orElseThrow();
        EtapaTreinoLlmDto recExtra = novas.stream()
                .filter(e -> "RECUPERACAO".equals(e.tipoEtapa())).findFirst().orElseThrow();

        assertThat(tiroExtra.fcAlvoEtapa()).isEqualTo("90-95% FCmax");
        assertThat(recExtra.fcAlvoEtapa()).isEqualTo("70-80% FCmax");
    }
}
