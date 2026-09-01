package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.ModoGeracaoPlano;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.TipoProva;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CA5 de refactor-llm-call-outside-transaction: tudo que o fluxo lê do atleta DEPOIS da chamada ao
 * LLM tem de estar inicializado quando o {@link PlanGenerationContextLoader} devolve — porque a
 * transação dele já acabou e as entidades estão detached.
 *
 * <p>A lista de acessos aqui espelha a tabela do design.md D2. Quem adicionar um acesso lazy
 * depois da fronteira e esquecer o loader vê este teste quebrar com
 * {@code LazyInitializationException}, não a produção.
 *
 * <p>Deliberadamente sem {@code @Transactional} na classe: o ponto é ler fora de qualquer
 * transação.
 */
@DisplayName("PlanGenerationContextLoader — contexto legível sem transação ativa")
class PlanGenerationContextLoaderIT extends AbstractIntegrationTest {

    @Autowired private PlanGenerationContextLoader loader;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private ProvaRepository provaRepository;

    private Assessoria assessoria;
    private Atleta atleta;

    @BeforeEach
    void preparar() {
        assessoria = new Assessoria();
        assessoria.setNome("Assessoria Fronteira");
        assessoria.setDominio("fronteira-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta novo = new Atleta();
        novo.setNome("Atleta Detached");
        novo.setObjetivo("Correr a meia em 1h50");
        novo.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        novo.setAtivo(AtletaStatus.ATIVO);
        novo.setDiasDisponiveis(List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SABADO));
        novo.setDiaPreferidoLongo(DiaSemana.SABADO);
        novo.setAssessoria(assessoria);
        atleta = atletaRepository.save(novo);

        Prova prova = new Prova();
        prova.setNomeProva("Meia da Fronteira");
        prova.setDataProva(LocalDate.now().plusWeeks(8));
        prova.setDistancia(DistanciaProva.KM_21);
        prova.setTipoProva(TipoProva.MEIA);
        prova.setProvaAlvo(true);
        prova.setAtleta(atleta);
        prova.setAssessoria(assessoria);
        provaRepository.save(prova);

        TenantContext.setTenantId(assessoria.getId());
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("devolve fora de transação, com provas, dias disponíveis e assessoria já inicializados")
    void contextoLegivelSemTransacao() {
        PlanGenerationContext ctx = loader.load(atleta.getId(), ModoGeracaoPlano.PROXIMA_SEMANA);

        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                .as("a transação de leitura tem de ter terminado quando o loader devolve")
                .isFalse();

        Atleta detached = ctx.atleta();
        assertThat(Hibernate.isInitialized(detached.getProvas())).as("provas").isTrue();
        assertThat(Hibernate.isInitialized(detached.getDiasDisponiveis())).as("dias disponíveis").isTrue();
        assertThat(Hibernate.isInitialized(detached.getAssessoria())).as("assessoria").isTrue();

        // Os mesmos acessos que o persister e o PlannerShadowService fazem depois do LLM — sem
        // sessão aberta, qualquer um deles estouraria LazyInitializationException se faltasse.
        assertThat(detached.getProvas()).hasSize(1);
        assertThat(detached.getDiasDisponiveis()).containsExactly(DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SABADO);
        assertThat(detached.getAssessoria().getId()).isEqualTo(assessoria.getId());
        assertThat(detached.getDiaPreferidoLongo()).isEqualTo(DiaSemana.SABADO);

        assertThat(ctx.proximaProva()).isNotNull();
        assertThat(ctx.proximaProva().getNomeProva()).isEqualTo("Meia da Fronteira");
        assertThat(ctx.metaDados().getId()).as("metadados criados na fase 1 e commitados").isNotNull();
        assertThat(ctx.semanaInicio()).isAfter(LocalDate.now());
    }
}
