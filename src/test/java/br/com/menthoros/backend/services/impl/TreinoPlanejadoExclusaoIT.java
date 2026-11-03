package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.TreinoPlanejadoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressão de {@code excluirTreino}: o plano gerenciado ainda carregava o treino removido na
 * coleção {@code treinosPlanejados} (cascade ALL), e o {@code save(plano)} fazia merge em cascata
 * do filho removido — {@code ObjectDeletedException: deleted instance passed to merge}, HTTP 500.
 *
 * <p>Só reproduz com Hibernate real: os testes com Mockito não têm cascade. Deliberadamente SEM
 * {@code @Transactional} na classe — o serviço tem de abrir e fechar a própria transação, como em
 * produção; um teste transacional reaproveitaria a sessão e esconderia o merge.
 */
@DisplayName("Exclusão de treino planejado — cascade do Hibernate contra Postgres real")
@TestPropertySource(properties = "onboarding.migrate-existing.enabled=false")
class TreinoPlanejadoExclusaoIT extends AbstractIntegrationTest {

    @Autowired private TreinoPlanejadoService treinoService;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private PlanoMetadadosRepository metadadosRepository;
    @Autowired private PlanoSemanalRepository planoSemanalRepository;
    @Autowired private TreinoPlanejadoRepository treinoRepository;

    private Assessoria assessoria;
    private Atleta atleta;
    private PlanoMetaDados metadados;
    private PlanoSemanal plano;

    @BeforeEach
    void preparar() {
        assessoria = new Assessoria();
        assessoria.setNome("Assessoria Exclusao");
        assessoria.setDominio("exclusao-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta novo = new Atleta();
        novo.setNome("Atleta Exclusao");
        novo.setObjetivo("Terminar os 10k");
        novo.setNivelExperiencia(NivelExperiencia.INICIANTE);
        novo.setAtivo(AtletaStatus.ATIVO);
        novo.setDiasDisponiveis(List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA));
        novo.setDiaPreferidoLongo(DiaSemana.QUARTA);
        novo.setAssessoria(assessoria);
        atleta = atletaRepository.save(novo);

        metadados = new PlanoMetaDados();
        metadados.setAtleta(atleta);
        metadados.setAssessoria(assessoria);
        metadados.setDiaPreferidoLongo(DiaSemana.QUARTA);
        metadados = metadadosRepository.save(metadados);

        LocalDate segunda = LocalDate.now().with(java.time.DayOfWeek.MONDAY).plusWeeks(1);
        plano = new PlanoSemanal();
        plano.setAtleta(atleta);
        plano.setAssessoria(assessoria);
        plano.setPlanoMetaDados(metadados);
        plano.setSemanaInicio(segunda);
        plano.setSemanaFim(segunda.plusDays(6));
        plano.setVolumePlanejadoKm(new BigDecimal("13.000"));
        plano.setVolumeAlvoKm(new BigDecimal("13.000"));
        plano.setStatus(PlanoStatus.PLANEJADO);
        plano.setReviewStatus(PlanoReviewStatus.AGUARDANDO_REVISAO);
        plano.setTreinosPlanejados(new ArrayList<>(List.of(
                treino(DiaSemana.SEGUNDA, segunda, "8.000"),
                treino(DiaSemana.QUARTA, segunda.plusDays(2), "5.000"))));
        plano = planoSemanalRepository.save(plano);

        TenantContext.setTenantId(assessoria.getId());
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
        planoSemanalRepository.deleteById(plano.getId());
        metadadosRepository.deleteById(metadados.getId());
        // Atleta é soft-delete (sem delete físico no repositório) e o domínio da assessoria é
        // aleatório por execução, então a massa restante não colide com outros testes.
    }

    @Test
    @DisplayName("exclui um treino: some do banco, o volume do plano cai e o outro treino permanece")
    void excluiTreinoSemObjectDeletedException() {
        UUID tenantId = assessoria.getId();
        List<TreinoPlanejado> antes = treinoRepository.findAll().stream()
                .filter(t -> plano.getId().equals(t.getPlanoSemanal().getId()))
                .toList();
        assertThat(antes).hasSize(2);
        UUID idSegunda = antes.stream()
                .filter(t -> t.getDiaSemana() == DiaSemana.SEGUNDA).findFirst().orElseThrow().getId();

        treinoService.excluirTreino(plano.getId(), idSegunda);

        assertThat(treinoRepository.findByIdAndPlanoSemanalIdAndTenantId(idSegunda, plano.getId(), tenantId))
                .as("o treino excluído não pode continuar no banco")
                .isEmpty();
        assertThat(treinoRepository.countByPlanoSemanalIdAndTenantId(plano.getId(), tenantId))
                .as("o outro treino do plano tem de permanecer")
                .isEqualTo(1);
        PlanoSemanal recarregado = planoSemanalRepository.findByIdAndTenantId(plano.getId(), tenantId).orElseThrow();
        assertThat(recarregado.getVolumePlanejadoKm())
                .as("13 km - 8 km do treino excluído")
                .isEqualByComparingTo("5.000");
    }

    @Test
    @DisplayName("exclui o último treino do plano: plano fica vazio e volume zera")
    void excluiUltimoTreino() {
        UUID tenantId = assessoria.getId();
        List<UUID> ids = treinoRepository.findAll().stream()
                .filter(t -> plano.getId().equals(t.getPlanoSemanal().getId()))
                .map(TreinoPlanejado::getId)
                .toList();

        ids.forEach(id -> treinoService.excluirTreino(plano.getId(), id));

        assertThat(treinoRepository.countByPlanoSemanalIdAndTenantId(plano.getId(), tenantId)).isZero();
        assertThat(planoSemanalRepository.findByIdAndTenantId(plano.getId(), tenantId).orElseThrow()
                .getVolumePlanejadoKm()).isEqualByComparingTo("0");
    }

    private TreinoPlanejado treino(DiaSemana dia, LocalDate data, String km) {
        TreinoPlanejado t = new TreinoPlanejado();
        t.setPlanoSemanal(plano);
        t.setAtleta(atleta);
        t.setTenantId(assessoria.getId());
        t.setDiaSemana(dia);
        t.setDataTreino(data);
        t.setTipoTreino(TipoTreino.CONTINUO);
        t.setDuracaoMin(Duration.ofMinutes(40));
        t.setDistanciaKm(new BigDecimal(km));
        return t;
    }
}
