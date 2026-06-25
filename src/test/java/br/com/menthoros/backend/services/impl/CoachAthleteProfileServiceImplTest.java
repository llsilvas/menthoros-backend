package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.AderenciasSemanalDto;
import br.com.menthoros.backend.dto.output.AtletaPerfilCoachOutputDto;
import br.com.menthoros.backend.dto.output.CoachAttentionItemOutputDto;
import br.com.menthoros.backend.dto.output.PmcPontoDto;
import br.com.menthoros.backend.dto.output.ProvaOutputDto;
import br.com.menthoros.backend.dto.output.RecordeDto;
import br.com.menthoros.backend.dto.output.SugestaoCoachOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.MotivoAtencao;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.Severidade;
import br.com.menthoros.backend.enums.StatusSugestao;
import br.com.menthoros.backend.enums.TipoSugestao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.enums.ConfiancaInferencia;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.mapper.ProvaMapper;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.CoachAttentionQueueService;
import br.com.menthoros.backend.services.PlanoService;
import br.com.menthoros.backend.services.SugestaoCoachService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("CoachAthleteProfileServiceImpl")
class CoachAthleteProfileServiceImplTest {

    @Mock private AtletaRepository atletaRepository;
    @Mock private AtletaProgressService atletaProgressService;
    @Mock private CoachAttentionQueueService coachAttentionQueueService;
    @Mock private SugestaoCoachService sugestaoCoachService;
    @Mock private PlanoService planoService;
    @Mock private PlanoMetadadosRepository planoMetadadosRepository;
    @Mock private ProvaRepository provaRepository;
    @Mock private ProvaMapper provaMapper;

    @InjectMocks
    private CoachAthleteProfileServiceImpl service;

    private UUID tenantId;
    private UUID atletaId;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        atleta = Atleta.builder()
                .id(atletaId)
                .nome("Ana")
                .sobrenome("Silva")
                .objetivo("Correr maratona em 4h")
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .build();
        lenient().when(provaRepository.findUpcomingByAtletaIdAndTenantId(eq(atletaId), eq(tenantId), any(LocalDate.class)))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("buscarPerfil")
    class BuscarPerfil {

        @Test
        @DisplayName("atleta de outro tenant → DomainNotFoundException, sub-serviços não chamados")
        void atletaDeOutroTenant() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.buscarPerfil(atletaId))
                    .isInstanceOf(DomainNotFoundException.class)
                    .hasMessageContaining("não encontrado");

            verifyNoInteractions(atletaProgressService, coachAttentionQueueService,
                    sugestaoCoachService, planoService);
        }

        @Test
        @DisplayName("happy path — retorna perfil completo com todos os blocos")
        void happyPath() {
            stubAtleta();
            stubPmc();
            stubAderencia();
            stubRecordes();
            when(planoService.findPlanoVigenteRelevante(atletaId, tenantId))
                    .thenReturn(Optional.empty());
            when(coachAttentionQueueService.getSinaisParaAtleta(atletaId, 3)).thenReturn(List.of());
            when(sugestaoCoachService.listarPorAtleta(atletaId)).thenReturn(List.of());

            AtletaPerfilCoachOutputDto perfil = service.buscarPerfil(atletaId);

            assertThat(perfil.atletaId()).isEqualTo(atletaId);
            assertThat(perfil.nomeAtleta()).isEqualTo("Ana Silva");
            assertThat(perfil.objetivo()).isEqualTo("Correr maratona em 4h");
            assertThat(perfil.nivelExperiencia()).isEqualTo("INTERMEDIARIO");
            assertThat(perfil.pmc()).hasSize(1);
            assertThat(perfil.aderenciaSemanal()).hasSize(1);
            assertThat(perfil.recordes()).hasSize(1);
            assertThat(perfil.planoVigente()).isNull();
            assertThat(perfil.proximaProva()).isNull();
            assertThat(perfil.sinaisRecentes()).isEmpty();
            assertThat(perfil.sugestoesRecentes()).isEmpty();
            assertThat(perfil.avisos()).isNull();
            assertThat(perfil.geradoEm()).isNotNull();
        }

        @Test
        @DisplayName("proximaProva — retorna a prova futura mais próxima e preserva marcação de prova alvo")
        void proximaProvaRetornaMenorDataFuturaPreservandoAlvo() {
            stubAtleta();
            stubPmc();
            stubAderencia();
            stubRecordes();
            when(planoService.findPlanoVigenteRelevante(atletaId, tenantId))
                    .thenReturn(Optional.empty());
            when(coachAttentionQueueService.getSinaisParaAtleta(atletaId, 3)).thenReturn(List.of());
            when(sugestaoCoachService.listarPorAtleta(atletaId)).thenReturn(List.of());

            Prova provaDistante = new Prova();
            Prova provaMaisProxima = new Prova();
            LocalDate hoje = LocalDate.now();
            ProvaOutputDto dtoDistante = provaDto("Meia preparatória", hoje.plusDays(30), false);
            ProvaOutputDto dtoMaisProxima = provaDto("Maratona alvo", hoje.plusDays(7), true);
            when(provaRepository.findUpcomingByAtletaIdAndTenantId(eq(atletaId), eq(tenantId), any(LocalDate.class)))
                    .thenReturn(List.of(provaDistante, provaMaisProxima));
            when(provaMapper.toOutputDto(provaDistante)).thenReturn(dtoDistante);
            when(provaMapper.toOutputDto(provaMaisProxima)).thenReturn(dtoMaisProxima);

            AtletaPerfilCoachOutputDto perfil = service.buscarPerfil(atletaId);

            assertThat(perfil.proximaProva()).isEqualTo(dtoMaisProxima);
            assertThat(perfil.proximaProva().nomeProva()).isEqualTo("Maratona alvo");
            assertThat(perfil.proximaProva().dataProva()).isEqualTo(hoje.plusDays(7));
            assertThat(perfil.proximaProva().provaAlvo()).isTrue();
            verify(provaRepository).findUpcomingByAtletaIdAndTenantId(eq(atletaId), eq(tenantId), any(LocalDate.class));
        }

        @Test
        @DisplayName("nome sem sobrenome — usa apenas nome")
        void nomeSemSobrenome() {
            atleta = Atleta.builder().id(atletaId).nome("Carlos").objetivo("Correr 10k").build();
            stubAtleta();
            when(atletaProgressService.getHistoricoPmc(eq(atletaId), any(), any())).thenReturn(List.of());
            when(atletaProgressService.getAderenciaSemanal(atletaId, 8)).thenReturn(List.of());
            when(atletaProgressService.getRecordes(atletaId)).thenReturn(List.of());
            when(planoService.findPlanoVigenteRelevante(atletaId, tenantId)).thenReturn(Optional.empty());
            when(coachAttentionQueueService.getSinaisParaAtleta(atletaId, 3)).thenReturn(List.of());
            when(sugestaoCoachService.listarPorAtleta(atletaId)).thenReturn(List.of());

            assertThat(service.buscarPerfil(atletaId).nomeAtleta()).isEqualTo("Carlos");
        }

        @Test
        @DisplayName("sub-serviço de PMC falha — avisos inclui 'pmc', demais blocos carregam")
        void falhaParialPmc() {
            stubAtleta();
            when(atletaProgressService.getHistoricoPmc(eq(atletaId), any(), any()))
                    .thenThrow(new RuntimeException("timeout PMC"));
            when(atletaProgressService.getAderenciaSemanal(atletaId, 8)).thenReturn(List.of());
            when(atletaProgressService.getRecordes(atletaId)).thenReturn(List.of());
            when(planoService.findPlanoVigenteRelevante(atletaId, tenantId)).thenReturn(Optional.empty());
            when(coachAttentionQueueService.getSinaisParaAtleta(atletaId, 3)).thenReturn(List.of());
            when(sugestaoCoachService.listarPorAtleta(atletaId)).thenReturn(List.of());

            AtletaPerfilCoachOutputDto perfil = service.buscarPerfil(atletaId);

            assertThat(perfil.pmc()).isEmpty();
            assertThat(perfil.avisos()).containsExactly("pmc");
            assertThat(perfil.aderenciaSemanal()).isEmpty();
        }

        @Test
        @DisplayName("plano APROVADO — treinos da semana são populados e ordenados por dataTreino")
        void planoAprovado() {
            stubAtleta();
            when(atletaProgressService.getHistoricoPmc(eq(atletaId), any(), any())).thenReturn(List.of());
            when(atletaProgressService.getAderenciaSemanal(atletaId, 8)).thenReturn(List.of());
            when(atletaProgressService.getRecordes(atletaId)).thenReturn(List.of());
            when(coachAttentionQueueService.getSinaisParaAtleta(atletaId, 3)).thenReturn(List.of());
            when(sugestaoCoachService.listarPorAtleta(atletaId)).thenReturn(List.of());

            PlanoSemanal plano = planoAprovadoComTreinos();
            when(planoService.findPlanoVigenteRelevante(atletaId, tenantId))
                    .thenReturn(Optional.of(plano));

            AtletaPerfilCoachOutputDto.PlanoVigenteDto planoVigente = service.buscarPerfil(atletaId).planoVigente();

            assertThat(planoVigente).isNotNull();
            assertThat(planoVigente.reviewStatus()).isEqualTo(PlanoReviewStatus.APROVADO);
            assertThat(planoVigente.treinos()).hasSize(2);
            assertThat(planoVigente.treinos().get(0).diaSemana()).isEqualTo("SEGUNDA");
            assertThat(planoVigente.treinos().get(0).statusExecucao()).isEqualTo("PENDENTE");
        }

        @Test
        @DisplayName("plano AGUARDANDO_REVISAO — treinos são retornados para permitir edição pelo coach")
        void planoAguardandoRevisao() {
            stubAtleta();
            when(atletaProgressService.getHistoricoPmc(eq(atletaId), any(), any())).thenReturn(List.of());
            when(atletaProgressService.getAderenciaSemanal(atletaId, 8)).thenReturn(List.of());
            when(atletaProgressService.getRecordes(atletaId)).thenReturn(List.of());
            when(coachAttentionQueueService.getSinaisParaAtleta(atletaId, 3)).thenReturn(List.of());
            when(sugestaoCoachService.listarPorAtleta(atletaId)).thenReturn(List.of());

            PlanoSemanal plano = planoAprovadoComTreinos();
            plano.setReviewStatus(PlanoReviewStatus.AGUARDANDO_REVISAO);
            when(planoService.findPlanoVigenteRelevante(atletaId, tenantId))
                    .thenReturn(Optional.of(plano));

            AtletaPerfilCoachOutputDto.PlanoVigenteDto planoVigente = service.buscarPerfil(atletaId).planoVigente();

            assertThat(planoVigente).isNotNull();
            assertThat(planoVigente.reviewStatus()).isEqualTo(PlanoReviewStatus.AGUARDANDO_REVISAO);
            assertThat(planoVigente.treinos()).hasSize(2);
            assertThat(planoVigente.treinos().get(0).diaSemana()).isEqualTo("SEGUNDA");
        }

        @Test
        @DisplayName("sinais do atleta retornados via getSinaisParaAtleta(atletaId, 3)")
        void sinaisDoAtleta() {
            stubAtleta();
            when(atletaProgressService.getHistoricoPmc(eq(atletaId), any(), any())).thenReturn(List.of());
            when(atletaProgressService.getAderenciaSemanal(atletaId, 8)).thenReturn(List.of());
            when(atletaProgressService.getRecordes(atletaId)).thenReturn(List.of());
            when(planoService.findPlanoVigenteRelevante(atletaId, tenantId)).thenReturn(Optional.empty());
            when(sugestaoCoachService.listarPorAtleta(atletaId)).thenReturn(List.of());

            CoachAttentionItemOutputDto sinal = sinal(atletaId, MotivoAtencao.FADIGA, Severidade.ALTA);
            when(coachAttentionQueueService.getSinaisParaAtleta(atletaId, 3))
                    .thenReturn(List.of(sinal));

            var sinais = service.buscarPerfil(atletaId).sinaisRecentes();

            assertThat(sinais).hasSize(1);
            assertThat(sinais.get(0).motivo()).isEqualTo(MotivoAtencao.FADIGA);
        }

        @Test
        @DisplayName("sugestões retornam o que o service entregar (limite aplicado no repositório)")
        void sugestoesPassthrough() {
            stubAtleta();
            when(atletaProgressService.getHistoricoPmc(eq(atletaId), any(), any())).thenReturn(List.of());
            when(atletaProgressService.getAderenciaSemanal(atletaId, 8)).thenReturn(List.of());
            when(atletaProgressService.getRecordes(atletaId)).thenReturn(List.of());
            when(planoService.findPlanoVigenteRelevante(atletaId, tenantId)).thenReturn(Optional.empty());
            when(coachAttentionQueueService.getSinaisParaAtleta(atletaId, 3)).thenReturn(List.of());
            when(sugestaoCoachService.listarPorAtleta(atletaId))
                    .thenReturn(List.of(
                            sugestao(TipoSugestao.RECOVERY),
                            sugestao(TipoSugestao.RECOVERY),
                            sugestao(TipoSugestao.RECOVERY)));

            var sugestoes = service.buscarPerfil(atletaId).sugestoesRecentes();

            assertThat(sugestoes).hasSize(3);
        }

        @Test
        @DisplayName("verifica que todas as chamadas aos sub-serviços usam o atletaId correto")
        void verificaParametrosSubServicos() {
            stubAtleta();
            when(atletaProgressService.getHistoricoPmc(eq(atletaId), any(), any())).thenReturn(List.of());
            when(atletaProgressService.getAderenciaSemanal(atletaId, 8)).thenReturn(List.of());
            when(atletaProgressService.getRecordes(atletaId)).thenReturn(List.of());
            when(planoService.findPlanoVigenteRelevante(atletaId, tenantId)).thenReturn(Optional.empty());
            when(coachAttentionQueueService.getSinaisParaAtleta(atletaId, 3)).thenReturn(List.of());
            when(sugestaoCoachService.listarPorAtleta(atletaId)).thenReturn(List.of());

            service.buscarPerfil(atletaId);

            verify(atletaProgressService).getHistoricoPmc(eq(atletaId), any(), any());
            verify(atletaProgressService).getAderenciaSemanal(atletaId, 8);
            verify(atletaProgressService).getRecordes(atletaId);
            verify(planoService).findPlanoVigenteRelevante(atletaId, tenantId);
            verify(coachAttentionQueueService).getSinaisParaAtleta(atletaId, 3);
            verify(sugestaoCoachService).listarPorAtleta(atletaId);
        }
    }

    @Nested
    @DisplayName("resolverLimiareisInferidos")
    class ResolverLimiareisInferidos {

        @Test
        @DisplayName("metaDados não encontrado → limiareisInferidos null no perfil")
        void metaDadosNulo_retornaLimiareisNulo() {
            stubAtleta();
            stubServicosSecundarios();

            AtletaPerfilCoachOutputDto perfil = service.buscarPerfil(atletaId);

            assertThat(perfil.limiareisInferidos()).isNull();
        }

        @Test
        @DisplayName("metaDados sem campos inferidos → limiareisInferidos null no perfil")
        void metaDadosSemCamposInferidos_retornaLimiareisNulo() {
            stubAtleta();
            stubServicosSecundarios();
            PlanoMetaDados metaDados = PlanoMetaDados.builder().atleta(atleta).build();
            when(planoMetadadosRepository.findLatestByAtletaIdAndTenantId(atletaId, tenantId))
                    .thenReturn(Optional.of(metaDados));

            AtletaPerfilCoachOutputDto perfil = service.buscarPerfil(atletaId);

            assertThat(perfil.limiareisInferidos()).isNull();
        }

        @Test
        @DisplayName("FC desatualizado com estimativa → fcLimiarEstimado preenchido no perfil")
        void fcDesatualizadoComEstimativa_retornaFcEstimado() {
            atleta.setFcLimiar(null);
            atleta.setDataUltimoTesteFc(null);
            stubAtleta();
            stubServicosSecundarios();
            PlanoMetaDados metaDados = PlanoMetaDados.builder()
                    .atleta(atleta)
                    .fcLimiarEstimado(163)
                    .confiancaInferenciaFc(ConfiancaInferencia.ALTA)
                    .dataInferenciaLimiar(LocalDate.of(2026, 6, 20))
                    .build();
            when(planoMetadadosRepository.findLatestByAtletaIdAndTenantId(atletaId, tenantId))
                    .thenReturn(Optional.of(metaDados));

            AtletaPerfilCoachOutputDto perfil = service.buscarPerfil(atletaId);

            assertThat(perfil.limiareisInferidos()).isNotNull();
            assertThat(perfil.limiareisInferidos().fcLimiarEstimado()).isEqualTo(163);
            assertThat(perfil.limiareisInferidos().confiancaInferenciaFc())
                    .isEqualTo(ConfiancaInferencia.ALTA);
            assertThat(perfil.limiareisInferidos().paceLimiarEstimadoFormatado()).isNull();
        }

        @Test
        @DisplayName("ambos os limiares atualizados (< 90 dias) → limiareisInferidos null no perfil")
        void ambosAtualizados_retornaLimiareisNulo() {
            atleta.setFcLimiar(165);
            atleta.setDataUltimoTesteFc(LocalDate.now().minusDays(30));
            atleta.setPaceLimiar(new BigDecimal("5.00"));
            atleta.setDataUltimoTestePace(LocalDate.now().minusDays(30));
            stubAtleta();
            stubServicosSecundarios();
            PlanoMetaDados metaDados = PlanoMetaDados.builder()
                    .atleta(atleta)
                    .fcLimiarEstimado(163)
                    .build();
            when(planoMetadadosRepository.findLatestByAtletaIdAndTenantId(atletaId, tenantId))
                    .thenReturn(Optional.of(metaDados));

            AtletaPerfilCoachOutputDto perfil = service.buscarPerfil(atletaId);

            assertThat(perfil.limiareisInferidos()).isNull();
        }

        private void stubServicosSecundarios() {
            when(atletaProgressService.getHistoricoPmc(eq(atletaId), any(), any())).thenReturn(List.of());
            when(atletaProgressService.getAderenciaSemanal(atletaId, 8)).thenReturn(List.of());
            when(atletaProgressService.getRecordes(atletaId)).thenReturn(List.of());
            when(planoService.findPlanoVigenteRelevante(atletaId, tenantId)).thenReturn(Optional.empty());
            when(coachAttentionQueueService.getSinaisParaAtleta(atletaId, 3)).thenReturn(List.of());
            when(sugestaoCoachService.listarPorAtleta(atletaId)).thenReturn(List.of());
        }
    }

    private void stubAtleta() {
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
    }

    private void stubPmc() {
        when(atletaProgressService.getHistoricoPmc(eq(atletaId), any(), any()))
                .thenReturn(List.of(new PmcPontoDto(LocalDate.now(), 50.0, 55.0, -5.0, 80)));
    }

    private void stubAderencia() {
        when(atletaProgressService.getAderenciaSemanal(atletaId, 8))
                .thenReturn(List.of(new AderenciasSemanalDto(LocalDate.of(2026, 6, 16), 5, 4, 80)));
    }

    private void stubRecordes() {
        when(atletaProgressService.getRecordes(atletaId))
                .thenReturn(List.of(new RecordeDto("5k", 1500L, LocalDate.of(2026, 5, 1), UUID.randomUUID())));
    }

    private PlanoSemanal planoAprovadoComTreinos() {
        TreinoPlanejado tp1 = new TreinoPlanejado();
        tp1.setDataTreino(LocalDate.of(2026, 6, 16));
        tp1.setDiaSemana(DiaSemana.SEGUNDA);
        tp1.setTipoTreino(TipoTreino.FACIL);
        tp1.setDistanciaKm(BigDecimal.valueOf(8.0));
        tp1.setStatusTreino(TreinoExecucaoStatus.PENDENTE);

        TreinoPlanejado tp2 = new TreinoPlanejado();
        tp2.setDataTreino(LocalDate.of(2026, 6, 18));
        tp2.setDiaSemana(DiaSemana.QUARTA);
        tp2.setTipoTreino(TipoTreino.CONTINUO);
        tp2.setDistanciaKm(BigDecimal.valueOf(12.0));
        tp2.setStatusTreino(TreinoExecucaoStatus.PENDENTE);

        return planoSemanalBuilder(PlanoReviewStatus.APROVADO, List.of(tp1, tp2));
    }

    private PlanoSemanal planoSemanalBuilder(PlanoReviewStatus reviewStatus, List<TreinoPlanejado> treinos) {
        PlanoSemanal plano = PlanoSemanal.builder()
                .id(UUID.randomUUID())
                .semanaInicio(LocalDate.of(2026, 6, 16))
                .semanaFim(LocalDate.of(2026, 6, 22))
                .reviewStatus(reviewStatus)
                .build();
        plano.setTreinosPlanejados(treinos);
        return plano;
    }

    private CoachAttentionItemOutputDto sinal(UUID atId, MotivoAtencao motivo, Severidade severidade) {
        return new CoachAttentionItemOutputDto(atId, "Atleta", severidade, 100, motivo,
                "Ação sugerida", Instant.now(), List.of(), null);
    }

    private SugestaoCoachOutputDto sugestao(TipoSugestao tipo) {
        return new SugestaoCoachOutputDto(UUID.randomUUID(), atletaId, "Ana", tipo,
                StatusSugestao.PENDING, "HIGH", "Recuperar", null, Instant.now(), null, null);
    }

    private ProvaOutputDto provaDto(String nome, LocalDate data, boolean provaAlvo) {
        return new ProvaOutputDto(
                UUID.randomUUID(),
                nome,
                data,
                null,
                null,
                null,
                provaAlvo,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                (int) ChronoUnit.DAYS.between(LocalDate.now(), data)
        );
    }
}
