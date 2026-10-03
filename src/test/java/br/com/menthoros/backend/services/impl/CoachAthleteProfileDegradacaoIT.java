package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.dto.output.AtletaPerfilCoachOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.services.CoachAthleteProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Prova, com transações reais, que a degradação por {@code avisos} do perfil funciona quando a
 * falha acontece DENTRO de um método {@code @Transactional} chamado pelo perfil.
 *
 * <p>Os testes com Mockito não exercitam isso: a exceção atravessa o proxy transacional do método
 * interno, que marca a transação do perfil (da qual ele participa) como rollback-only. O
 * {@code buscarLista} captura a exceção, mas o commit do perfil falhava com
 * {@code UnexpectedRollbackException} — o coach recebia erro em vez do perfil sem o bloco. Achado
 * do Codex na /qa de fix-coach-diagnosis-charts.
 *
 * <p>Sem {@code @Transactional} na classe: com a transação do teste por fora, o perfil participaria
 * dela e o sintoma só apareceria no fim do teste.
 */
class CoachAthleteProfileDegradacaoIT extends AbstractIntegrationTest {

    @Autowired
    private CoachAthleteProfileService coachAthleteProfileService;
    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private AtletaRepository atletaRepository;

    /** Usado só por {@code getHistoricoPmc} no caminho do perfil. */
    @MockitoBean
    private MetricasDiariasRepository metricasDiariasRepository;

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("falha dentro de getHistoricoPmc vira aviso 'pmc', sem UnexpectedRollbackException")
    void falhaTransacionalDegrada() {
        Atleta atleta = seedAtleta();
        TenantContext.setTenantId(atleta.getAssessoria().getId());
        when(metricasDiariasRepository.findByAtletaIdAndDataBetweenOrderByDataAsc(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("falha simulada na série PMC"));

        AtletaPerfilCoachOutputDto perfil = coachAthleteProfileService.buscarPerfil(atleta.getId());

        assertThat(perfil.avisos()).containsExactly("pmc");
        assertThat(perfil.pmc()).isEmpty();
        assertThat(perfil.nomeAtleta()).isEqualTo("Atleta Degradacao");
    }

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Degradacao Test");
        assessoria.setDominio("degradacao-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Degradacao");
        atleta.setEmail("degradacao-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 21km");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }
}
