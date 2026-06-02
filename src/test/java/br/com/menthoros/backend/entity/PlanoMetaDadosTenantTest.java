package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para garantir que PlanoMetaDados é corretamente
 * isolado por tenant (assessoria).
 *
 * Section 5 — TDD: estes testes devem falhar (RED) antes da implementação
 * do campo assessoria em PlanoMetaDados.
 */
@Transactional
class PlanoMetaDadosTenantTest extends AbstractIntegrationTest {

    @Autowired
    private AssessoriaRepository assessoriaRepository;

    @Autowired
    private AtletaRepository atletaRepository;

    @Autowired
    private PlanoMetadadosRepository planoMetadadosRepository;

    private Assessoria assessoria1;
    private Assessoria assessoria2;

    @BeforeEach
    void setup() {
        assessoria1 = Assessoria.builder()
                .nome("Assessoria Tenant 1")
                .dominio("tenant1-" + UUID.randomUUID())
                .plano(PlanoAssessoria.BASIC)
                .build();
        assessoria1 = assessoriaRepository.save(assessoria1);

        assessoria2 = Assessoria.builder()
                .nome("Assessoria Tenant 2")
                .dominio("tenant2-" + UUID.randomUUID())
                .plano(PlanoAssessoria.BASIC)
                .build();
        assessoria2 = assessoriaRepository.save(assessoria2);
    }

    @Test
    @DisplayName("5.1 — PlanoMetaDados deve persistir com assessoria (tenant) corretamente")
    void persisteComAssessoria() {
        // Criar atleta vinculado à assessoria1
        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Tenant Test");
        atleta.setObjetivo("Correr maratona");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria1);
        atleta = atletaRepository.save(atleta);

        // Criar PlanoMetaDados vinculado à assessoria1 (tenant)
        PlanoMetaDados metaDados = PlanoMetaDados.builder()
                .atleta(atleta)
                .diaPreferidoLongo(DiaSemana.SABADO)
                .assessoria(assessoria1)
                .build();
        metaDados = planoMetadadosRepository.save(metaDados);

        // Buscar pelo id e verificar que assessoria está correta
        PlanoMetaDados found = planoMetadadosRepository.findById(metaDados.getId()).orElseThrow();
        assertThat(found.getAssessoria()).isNotNull();
        assertThat(found.getAssessoria().getId()).isEqualTo(assessoria1.getId());
    }

    @Test
    @DisplayName("5.2 — findByAtleta retorna apenas metaDados do tenant correto")
    void findByAtleta_retornaApenasMesmoTenant() {
        // Criar atleta no tenant1
        Atleta atletaTenant1 = new Atleta();
        atletaTenant1.setNome("Atleta no Tenant 1");
        atletaTenant1.setObjetivo("Treinar muito");
        atletaTenant1.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atletaTenant1.setAtivo(AtletaStatus.ATIVO);
        atletaTenant1.setAssessoria(assessoria1);
        atletaTenant1 = atletaRepository.save(atletaTenant1);

        // Criar atleta no tenant2 (ID diferente, mesmo atleta simulado)
        Atleta atletaTenant2 = new Atleta();
        atletaTenant2.setNome("Atleta no Tenant 2");
        atletaTenant2.setObjetivo("Treinar muito");
        atletaTenant2.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atletaTenant2.setAtivo(AtletaStatus.ATIVO);
        atletaTenant2.setAssessoria(assessoria2);
        atletaTenant2 = atletaRepository.save(atletaTenant2);

        // Criar PlanoMetaDados para atleta do tenant1
        PlanoMetaDados metaDadosTenant1 = PlanoMetaDados.builder()
                .atleta(atletaTenant1)
                .diaPreferidoLongo(DiaSemana.SABADO)
                .assessoria(assessoria1)
                .build();
        planoMetadadosRepository.save(metaDadosTenant1);

        // Criar PlanoMetaDados para atleta do tenant2
        PlanoMetaDados metaDadosTenant2 = PlanoMetaDados.builder()
                .atleta(atletaTenant2)
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .assessoria(assessoria2)
                .build();
        planoMetadadosRepository.save(metaDadosTenant2);

        // Buscar metaDados do atleta do tenant1 — deve retornar apenas do tenant1
        var result = planoMetadadosRepository.findByAtletaIdAndAssessoriaId(
                atletaTenant1.getId(), assessoria1.getId());
        assertThat(result).isPresent();
        assertThat(result.get().getAssessoria().getId()).isEqualTo(assessoria1.getId());

        // Buscar metaDados do atleta do tenant1 usando assessoria2 — não deve retornar
        var resultCrossTenant = planoMetadadosRepository.findByAtletaIdAndAssessoriaId(
                atletaTenant1.getId(), assessoria2.getId());
        assertThat(resultCrossTenant).isEmpty();
    }
}
