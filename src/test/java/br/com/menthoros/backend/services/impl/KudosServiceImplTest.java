package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.KudosInputDto;
import br.com.menthoros.backend.dto.output.KudosOutputDto;
import br.com.menthoros.backend.dto.output.KudosRecenteOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Kudos;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.MotivoKudos;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DuplicateResourceException;
import br.com.menthoros.backend.mapper.KudosMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.KudosRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KudosServiceImplTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 6, 17);

    @Mock private KudosRepository kudosRepository;
    @Mock private AtletaRepository atletaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AuthenticatedPrincipalResolver principalResolver;

    private KudosMapper kudosMapper;
    private KudosServiceImpl service;

    private UUID tenantId;
    private UUID atletaId;
    private UUID coachId;
    private Atleta atleta;
    private Usuario coach;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        coachId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        kudosMapper = new KudosMapper();
        Clock clock = Clock.fixed(Instant.parse("2026-06-17T12:00:00Z"), ZoneOffset.UTC);
        service = new KudosServiceImpl(kudosRepository, atletaRepository, usuarioRepository, kudosMapper, principalResolver, clock);

        atleta = mock(Atleta.class);
        lenient().when(atleta.getId()).thenReturn(atletaId);
        coach = mock(Usuario.class);
        lenient().when(coach.getId()).thenReturn(coachId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("registrar")
    class Registrar {

        @BeforeEach
        void stubResolucao() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            // lenient: não alcançado pelo teste que rejeita antes de resolver o coach (atletaNaoEncontrado)
            lenient().when(principalResolver.getCurrentSubject()).thenReturn("coach-sub");
            lenient().when(usuarioRepository.findByKeycloakIdAndAssessoria_Id("coach-sub", tenantId)).thenReturn(Optional.of(coach));
        }

        @Test
        @DisplayName("cria o kudo quando não há duplicata no dia")
        void criaKudo() {
            when(kudosRepository.existsByAtletaIdAndCoachIdAndMotivoAndData(atletaId, coachId, MotivoKudos.CONSISTENCIA, HOJE))
                    .thenReturn(false);
            ArgumentCaptor<Kudos> captor = ArgumentCaptor.forClass(Kudos.class);
            when(kudosRepository.save(captor.capture())).thenAnswer(inv -> {
                Kudos k = inv.getArgument(0);
                k.setId(UUID.randomUUID());
                k.setCreatedAt(Instant.now());
                return k;
            });

            KudosOutputDto out = service.registrar(atletaId, new KudosInputDto(MotivoKudos.CONSISTENCIA));

            assertThat(out.atletaId()).isEqualTo(atletaId);
            assertThat(out.coachId()).isEqualTo(coachId);
            assertThat(out.motivo()).isEqualTo(MotivoKudos.CONSISTENCIA);
            Kudos salvo = captor.getValue();
            assertThat(salvo.getData()).isEqualTo(HOJE);
            assertThat(salvo.getTenantId()).isEqualTo(tenantId);
        }

        @Test
        @DisplayName("lança DuplicateResourceException (409) ao repetir o mesmo motivo/atleta/coach no mesmo dia")
        void rejeitaDuplicataMesmoDia() {
            when(kudosRepository.existsByAtletaIdAndCoachIdAndMotivoAndData(atletaId, coachId, MotivoKudos.CONSISTENCIA, HOJE))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.registrar(atletaId, new KudosInputDto(MotivoKudos.CONSISTENCIA)))
                    .isInstanceOf(DuplicateResourceException.class);

            verify(kudosRepository, never()).save(any());
        }

        @Test
        @DisplayName("motivo diferente no mesmo dia não é bloqueado")
        void permiteMotivoDiferenteMesmoDia() {
            when(kudosRepository.existsByAtletaIdAndCoachIdAndMotivoAndData(atletaId, coachId, MotivoKudos.ESFORCO, HOJE))
                    .thenReturn(false);
            when(kudosRepository.save(any())).thenAnswer(inv -> {
                Kudos k = inv.getArgument(0);
                k.setId(UUID.randomUUID());
                k.setCreatedAt(Instant.now());
                return k;
            });

            KudosOutputDto out = service.registrar(atletaId, new KudosInputDto(MotivoKudos.ESFORCO));

            assertThat(out.motivo()).isEqualTo(MotivoKudos.ESFORCO);
            verify(kudosRepository).save(any());
        }

        @Test
        @DisplayName("atleta não encontrado no tenant lança DomainNotFoundException")
        void atletaNaoEncontrado() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.registrar(atletaId, new KudosInputDto(MotivoKudos.CONSISTENCIA)))
                    .isInstanceOf(DomainNotFoundException.class);

            verifyNoInteractions(kudosRepository);
        }

        @Test
        @DisplayName("coach autenticado não encontrado no tenant lança DomainNotFoundException")
        void coachNaoEncontrado() {
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id("coach-sub", tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.registrar(atletaId, new KudosInputDto(MotivoKudos.CONSISTENCIA)))
                    .isInstanceOf(DomainNotFoundException.class);

            verifyNoInteractions(kudosRepository);
        }

        @ParameterizedTest(name = "motivo={0}")
        @DisplayName("aceita todo o enum MotivoKudos")
        @EnumSource(MotivoKudos.class)
        void aceitaTodoOEnum(MotivoKudos motivo) {
            when(kudosRepository.existsByAtletaIdAndCoachIdAndMotivoAndData(eq(atletaId), eq(coachId), eq(motivo), eq(HOJE)))
                    .thenReturn(false);
            when(kudosRepository.save(any())).thenAnswer(inv -> {
                Kudos k = inv.getArgument(0);
                k.setId(UUID.randomUUID());
                k.setCreatedAt(Instant.now());
                return k;
            });

            assertThat(service.registrar(atletaId, new KudosInputDto(motivo)).motivo()).isEqualTo(motivo);
        }
    }

    @Nested
    @DisplayName("listarRecentes")
    class ListarRecentes {

        private static final Instant DESDE = Instant.parse("2026-06-17T12:00:00Z")
                .minus(KudosServiceImpl.JANELA_KUDOS_RECENTES_DIAS, ChronoUnit.DAYS);

        @Test
        @DisplayName("retorna os kudos mapeados, mais recente primeiro")
        void retornaKudos() {
            Kudos k1 = Kudos.builder().id(UUID.randomUUID()).motivo(MotivoKudos.CONSISTENCIA).createdAt(Instant.now()).build();
            when(kudosRepository.findRecentesByAtletaIdAndTenantId(atletaId, tenantId, DESDE))
                    .thenReturn(List.of(k1));

            List<KudosRecenteOutputDto> out = service.listarRecentes(atletaId);

            assertThat(out).hasSize(1);
            assertThat(out.get(0).motivo()).isEqualTo(MotivoKudos.CONSISTENCIA);
        }

        @Test
        @DisplayName("sem kudos retorna lista vazia, não erro")
        void semKudosListaVazia() {
            when(kudosRepository.findRecentesByAtletaIdAndTenantId(atletaId, tenantId, DESDE))
                    .thenReturn(List.of());

            assertThat(service.listarRecentes(atletaId)).isEmpty();
        }

        @Test
        @DisplayName("calcula a janela de 7 dias a partir do clock injetado")
        void calculaJanelaDeSeteDias() {
            when(kudosRepository.findRecentesByAtletaIdAndTenantId(eq(atletaId), eq(tenantId), any()))
                    .thenReturn(List.of());

            service.listarRecentes(atletaId);

            verify(kudosRepository).findRecentesByAtletaIdAndTenantId(atletaId, tenantId, DESDE);
        }
    }
}
