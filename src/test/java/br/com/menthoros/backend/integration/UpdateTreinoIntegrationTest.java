package br.com.menthoros.backend.integration;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.dto.input.TreinoRealizadoInputDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.events.TreinoRegistradoEvent;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.TreinoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RecordApplicationEvents
@DisplayName("updateTreino — integração com banco real")
class UpdateTreinoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TreinoService treinoService;

    @Autowired
    private TreinoRealizadoRepository treinoRealizadoRepository;

    @Autowired
    private AtletaRepository atletaRepository;

    @Autowired
    private AssessoriaRepository assessoriaRepository;

    @Autowired
    private PlanoMetadadosRepository planoMetadadosRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    private UUID tenantId;
    private TreinoRealizado treinoExistente;

    @BeforeEach
    void setUp() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Update Test");
        assessoria.setDominio("update-test-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);
        tenantId = assessoria.getId();

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Update");
        atleta.setEmail("update-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);

        // reprocessar (task 7.3) recalcula TSB via TsbService.atualizarMetaDados, que exige
        // PlanoMetaDados existente para o atleta — sem isso, "MetaDados não encontrado".
        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(assessoria);
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        planoMetadadosRepository.save(meta);

        treinoExistente = new TreinoRealizado();
        treinoExistente.setAtleta(atleta);
        treinoExistente.setDataTreino(LocalDate.now());
        treinoExistente.setDiaSemana(DiaSemana.SEGUNDA);
        treinoExistente.setTipoTreino(TipoTreino.FACIL);
        treinoExistente.setDuracaoMin(Duration.ofMinutes(45));
        treinoExistente.setFonteDados(FonteDados.STRAVA);
        treinoExistente.setTenantId(tenantId);
        treinoExistente = treinoRealizadoRepository.save(treinoExistente);

        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // -------------------------------------------------------------------------
    // Update básico — campos observacionais persistidos
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deve persistir campos mutáveis após update")
    void updateTreino_persistsMutableFields() {
        TreinoRealizadoInputDto dto = buildDto(7, "Treino pesado", 155, 180);

        TreinoRealizadoOutputDto result = treinoService.updateTreino(treinoExistente.getId(), dto);

        assertThat(result).isNotNull();
        assertThat(result.percepcaoEsforco()).isEqualTo(7);
        assertThat(result.feedbackAtleta()).isEqualTo("Treino pesado");
        assertThat(result.fcMedia()).isEqualTo(155);
        assertThat(result.fcMax()).isEqualTo(180);
    }

    // -------------------------------------------------------------------------
    // Gate RPE → evento
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deve publicar TreinoRegistradoEvent quando percepcaoEsforco não for nulo")
    void updateTreino_withRpe_publishesEvent() {
        TreinoRealizadoInputDto dto = buildDto(8, null, null, null);

        treinoService.updateTreino(treinoExistente.getId(), dto);

        long count = applicationEvents.stream(TreinoRegistradoEvent.class).count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("não deve publicar evento quando percepcaoEsforco for nulo")
    void updateTreino_withoutRpe_doesNotPublishEvent() {
        TreinoRealizadoInputDto dto = buildDto(null, "Feedback sem RPE", null, null);

        treinoService.updateTreino(treinoExistente.getId(), dto);

        long count = applicationEvents.stream(TreinoRegistradoEvent.class).count();
        assertThat(count).isZero();
    }

    // -------------------------------------------------------------------------
    // Isolamento de tenant
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deve lançar DomainNotFoundException para treino de outro tenant")
    void updateTreino_wrongTenant_throwsNotFound() {
        TenantContext.setTenantId(UUID.randomUUID()); // outro tenant

        TreinoRealizadoInputDto dto = buildDto(7, null, null, null);

        assertThatThrownBy(() -> treinoService.updateTreino(treinoExistente.getId(), dto))
                .isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    @DisplayName("deve lançar DomainNotFoundException para UUID inexistente")
    void updateTreino_unknownId_throwsNotFound() {
        TreinoRealizadoInputDto dto = buildDto(7, null, null, null);

        assertThatThrownBy(() -> treinoService.updateTreino(UUID.randomUUID(), dto))
                .isInstanceOf(DomainNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // Imutabilidade de campos estruturais
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("não deve sobrescrever campos imutáveis (fonteDados, dataTreino, tipoTreino)")
    void updateTreino_doesNotOverwriteImmutableFields() {
        // atletaId, planoSemanalId, treinoPlanejadoId, dataTreino, diaSemana, tipoTreino
        // descricao, zonaAlvo, duracaoMin, distanciaKm, ritmoAlvo, ritmoMedio
        // elevacaoGanhoMetros, elevacaoPerdaMetros, observacao
        // fcMedia, fcMax, cadenciaMedia, potenciaMedia, velocidadeMedia
        // percepcaoEsforco, feedbackAtleta, qualidadeSonoNoiteAnterior, nivelEstresse
        // fonteDados, status, externalId, etapasRealizadas
        TreinoRealizadoInputDto dto = new TreinoRealizadoInputDto(
                UUID.randomUUID(),            // atletaId — ignorado
                UUID.randomUUID(),            // planoSemanalId — ignorado
                UUID.randomUUID(),            // treinoPlanejadoId — ignorado
                LocalDate.now().plusDays(99), // dataTreino — ignorada
                DiaSemana.DOMINGO,            // diaSemana — ignorado
                TipoTreino.LONGO,             // tipoTreino — ignorado
                "nova desc",                  // descricao
                null,                         // zonaAlvo
                null,                         // duracaoMin
                null,                         // distanciaKm
                null,                         // ritmoAlvo
                null,                         // ritmoMedio
                null,                         // elevacaoGanhoMetros
                null,                         // elevacaoPerdaMetros
                null,                         // observacao
                null,                         // fcMedia
                null,                         // fcMax
                null,                         // cadenciaMedia
                null,                         // potenciaMedia
                null,                         // velocidadeMedia
                6,                            // percepcaoEsforco
                "feedback",                   // feedbackAtleta
                null,                         // qualidadeSonoNoiteAnterior
                null,                         // nivelEstresse
                FonteDados.GARMIN,            // fonteDados — ignorada
                TreinoExecucaoStatus.REALIZADO,
                "novo-external-id",           // externalId — ignorado
                null                          // etapasRealizadas
        );

        TreinoRealizadoOutputDto result = treinoService.updateTreino(treinoExistente.getId(), dto);

        assertThat(result.tipoTreino()).isEqualTo(TipoTreino.FACIL);   // original
        assertThat(result.fonteDados()).isEqualTo(FonteDados.STRAVA);   // original
        assertThat(result.dataTreino()).isEqualTo(LocalDate.now());     // original
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TreinoRealizadoInputDto buildDto(Integer rpe, String feedback, Integer fcMedia, Integer fcMax) {
        // atletaId, planoSemanalId, treinoPlanejadoId, dataTreino, diaSemana, tipoTreino
        // descricao, zonaAlvo, duracaoMin, distanciaKm, ritmoAlvo, ritmoMedio
        // elevacaoGanhoMetros, elevacaoPerdaMetros, observacao
        // fcMedia, fcMax, cadenciaMedia, potenciaMedia, velocidadeMedia
        // percepcaoEsforco, feedbackAtleta, qualidadeSonoNoiteAnterior, nivelEstresse
        // fonteDados, status, externalId, etapasRealizadas
        return new TreinoRealizadoInputDto(
                null, null, null,        // ids — ignorados no update
                null, null, null,        // dataTreino, diaSemana, tipoTreino — imutáveis
                null, null, null, null, null, null, // descricao, zonaAlvo, duracaoMin, distanciaKm, ritmoAlvo, ritmoMedio
                null, null, null,        // elevacaoGanhoMetros, elevacaoPerdaMetros, observacao
                fcMedia, fcMax,          // métricas cardíacas
                null, null, null,        // cadenciaMedia, potenciaMedia, velocidadeMedia
                rpe,                     // percepcaoEsforco — gate da análise AI
                feedback,                // feedbackAtleta
                null, null,              // qualidadeSonoNoiteAnterior, nivelEstresse
                null,                    // fonteDados — imutável
                TreinoExecucaoStatus.REALIZADO,
                null,                    // externalId — imutável
                null                     // etapasRealizadas
        );
    }
}
