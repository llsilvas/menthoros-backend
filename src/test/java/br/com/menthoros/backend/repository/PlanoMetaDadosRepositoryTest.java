package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * refactor-threshold-call-outside-transaction, design.md D1: projeção pro valor anterior de
 * {@code paceLimiarEstimado}, usado só pelo log de outlier — nunca {@code buscarOuCriarMetadados},
 * que cria o registro se não existir (mutação fora de transação).
 */
class PlanoMetaDadosRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private PlanoMetadadosRepository planoMetaDadosRepository;

    @Autowired
    private AtletaRepository atletaRepository;

    @Autowired
    private AssessoriaRepository assessoriaRepository;

    private Assessoria tenant;
    private Atleta atleta;

    @BeforeEach
    void setup() {
        tenant = new Assessoria();
        tenant.setNome("Assessoria PlanoMetaDados Test");
        tenant.setDominio("pmd-test-" + UUID.randomUUID());
        tenant.setPlano(PlanoAssessoria.BASIC);
        tenant = assessoriaRepository.save(tenant);

        atleta = new Atleta();
        atleta.setNome("Atleta PMD Test");
        atleta.setEmail("pmd-test-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(tenant);
        atleta = atletaRepository.save(atleta);
    }

    @Test
    @DisplayName("findPaceLimiarEstimadoByAtletaId: devolve o valor quando o registro existe")
    void findPaceLimiarEstimadoByAtletaId_devolveValor() {
        PlanoMetaDados metaDados = PlanoMetaDados.builder()
                .atleta(atleta)
                .assessoria(tenant)
                .dataCriacao(java.time.LocalDateTime.now())
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .paceLimiarEstimado(new BigDecimal("4.5000"))
                .build();
        planoMetaDadosRepository.save(metaDados);

        Optional<BigDecimal> result = planoMetaDadosRepository.findPaceLimiarEstimadoByAtletaId(atleta.getId());

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo("4.5000");
    }

    @Test
    @DisplayName("findPaceLimiarEstimadoByAtletaId: devolve vazio se o atleta não tem PlanoMetaDados ainda")
    void findPaceLimiarEstimadoByAtletaId_semRegistroDevolveVazio() {
        Optional<BigDecimal> result = planoMetaDadosRepository.findPaceLimiarEstimadoByAtletaId(atleta.getId());

        assertThat(result).isEmpty();
    }
}
