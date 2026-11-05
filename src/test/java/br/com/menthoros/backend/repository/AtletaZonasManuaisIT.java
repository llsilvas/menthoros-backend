package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.FaixaZonaManual;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.ModoZonas;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V108 — modo de zonas por eixo e faixas manuais em JSONB (coach-zonas-manuais, D1). */
@DisplayName("V108 zonas manuais: modo por eixo, JSONB ida e volta, UPDATE por eixo")
class AtletaZonasManuaisIT extends AbstractIntegrationTest {

    private static final List<FaixaZonaManual> FC = List.of(
            new FaixaZonaManual(1, 120, 140), new FaixaZonaManual(2, 140, 150),
            new FaixaZonaManual(3, 153, 160), new FaixaZonaManual(4, 160, 168),
            new FaixaZonaManual(5, 168, 178));

    private static final List<FaixaZonaManual> PACE = List.of(
            new FaixaZonaManual(1, 345, 375), new FaixaZonaManual(2, 315, 345),
            new FaixaZonaManual(3, 300, 315), new FaixaZonaManual(4, 285, 300),
            new FaixaZonaManual(5, 270, 285), new FaixaZonaManual(6, 246, 270));

    @Autowired
    private AtletaRepository atletaRepository;

    @Autowired
    private AssessoriaRepository assessoriaRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private Assessoria tenant;
    private Atleta atleta;

    @BeforeEach
    void setup() {
        tenant = new Assessoria();
        tenant.setNome("Assessoria Zonas Manuais");
        tenant.setDominio("zonas-manuais-" + UUID.randomUUID());
        tenant.setPlano(PlanoAssessoria.BASIC);
        tenant = assessoriaRepository.save(tenant);

        atleta = new Atleta();
        atleta.setNome("Atleta Zonas");
        atleta.setEmail("zonas-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(tenant);
        atleta = atletaRepository.save(atleta);
    }

    private Atleta reler() {
        return atletaRepository.findByIdAndTenantId(atleta.getId(), tenant.getId()).orElseThrow();
    }

    @Test
    @DisplayName("atleta novo nasce com os dois eixos em LIMIAR e sem faixas")
    void padraoLimiar() {
        Atleta salvo = reler();

        assertThat(salvo.getModoZonasFc()).isEqualTo(ModoZonas.LIMIAR);
        assertThat(salvo.getModoZonasPace()).isEqualTo(ModoZonas.LIMIAR);
        assertThat(salvo.getZonasManuaisFc()).isNull();
        assertThat(salvo.getZonasManuaisPace()).isNull();
    }

    @Test
    @DisplayName("gravar FC manual e depois pace manual preserva os dois eixos")
    void updatePorEixoPreservaOOutro() {
        assertThat(atletaRepository.atualizarZonasFc(atleta.getId(), tenant.getId(), ModoZonas.MANUAL, FC)).isEqualTo(1);
        assertThat(atletaRepository.atualizarZonasPace(atleta.getId(), tenant.getId(), ModoZonas.MANUAL, PACE)).isEqualTo(1);

        Atleta salvo = reler();
        assertThat(salvo.getModoZonasFc()).isEqualTo(ModoZonas.MANUAL);
        assertThat(salvo.getZonasManuaisFc()).containsExactlyElementsOf(FC);
        assertThat(salvo.getModoZonasPace()).isEqualTo(ModoZonas.MANUAL);
        assertThat(salvo.getZonasManuaisPace()).containsExactlyElementsOf(PACE);
    }

    @Test
    @DisplayName("voltar para LIMIAR com faixas nulas apaga as faixas daquele eixo")
    void voltarParaLimiarApaga() {
        atletaRepository.atualizarZonasFc(atleta.getId(), tenant.getId(), ModoZonas.MANUAL, FC);
        atletaRepository.atualizarZonasFc(atleta.getId(), tenant.getId(), ModoZonas.LIMIAR, null);

        Atleta salvo = reler();
        assertThat(salvo.getModoZonasFc()).isEqualTo(ModoZonas.LIMIAR);
        assertThat(salvo.getZonasManuaisFc()).isNull();
    }

    @Test
    @DisplayName("save da entidade carregada antes não restaura as zonas antigas (lost update)")
    void saveDeEntidadeVelhaPreservaZonas() {
        Atleta velho = reler();
        atletaRepository.atualizarZonasFc(atleta.getId(), tenant.getId(), ModoZonas.MANUAL, FC);

        velho.setNome("Nome Editado");
        atletaRepository.save(velho);

        Atleta salvo = reler();
        assertThat(salvo.getNome()).isEqualTo("Nome Editado");
        assertThat(salvo.getModoZonasFc()).isEqualTo(ModoZonas.MANUAL);
        assertThat(salvo.getZonasManuaisFc()).containsExactlyElementsOf(FC);
    }

    @Test
    @DisplayName("gravar zonas avança o updated_at do atleta (base da métrica 3.1)")
    void updateAvancaUpdatedAt() {
        java.sql.Timestamp antes = java.sql.Timestamp.valueOf("2020-01-01 00:00:00");
        jdbc.update("UPDATE tb_atleta SET updated_at = ? WHERE id = ?", antes, atleta.getId());

        atletaRepository.atualizarZonasPace(atleta.getId(), tenant.getId(), ModoZonas.MANUAL, PACE);

        java.sql.Timestamp depois = jdbc.queryForObject(
                "SELECT updated_at FROM tb_atleta WHERE id = ?", java.sql.Timestamp.class, atleta.getId());
        assertThat(depois).isAfter(antes);
    }

    @Test
    @DisplayName("UPDATE de outro tenant não toca o atleta")
    void outroTenant() {
        assertThat(atletaRepository.atualizarZonasFc(atleta.getId(), UUID.randomUUID(), ModoZonas.MANUAL, FC)).isZero();
        assertThat(reler().getModoZonasFc()).isEqualTo(ModoZonas.LIMIAR);
    }

    @Test
    @DisplayName("CHECK rejeita eixo MANUAL sem faixas")
    void checkRejeitaManualSemFaixas() {
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE tb_atleta SET modo_zonas_pace = 'MANUAL', zonas_manuais_pace = NULL WHERE id = ?",
                atleta.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CHECK rejeita modo fora do enum")
    void checkRejeitaModoInvalido() {
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE tb_atleta SET modo_zonas_fc = 'OUTRO' WHERE id = ?", atleta.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
