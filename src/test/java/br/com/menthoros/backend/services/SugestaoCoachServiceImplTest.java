package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.RejeitarSugestaoRequestDto;
import br.com.menthoros.backend.dto.output.SugestaoCoachOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.SugestaoCoach;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.StatusSugestao;
import br.com.menthoros.backend.enums.TipoSugestao;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.mapper.SugestaoCoachMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.SugestaoCoachRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import br.com.menthoros.backend.services.impl.SugestaoCoachServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SugestaoCoachServiceImplTest {

    @Mock private SugestaoCoachRepository repository;
    @Mock private SugestaoCoachMapper mapper;
    @Mock private AuthenticatedPrincipalResolver principalResolver;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks private SugestaoCoachServiceImpl service;

    private UUID tenantId;
    private UUID sugestaoId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        sugestaoId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    /** Estabiliza o par sub/Usuario resolvido pelo security context — usado só nos testes que chegam ao ramo PENDING. */
    private UUID stubReviewedBy() {
        UUID usuarioId = UUID.randomUUID();
        String sub = "keycloak-sub-" + usuarioId;
        Usuario usuario = Usuario.builder().id(usuarioId).build();
        when(principalResolver.getCurrentSubject()).thenReturn(sub);
        when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId)).thenReturn(Optional.of(usuario));
        return usuarioId;
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private SugestaoCoach sugestao(StatusSugestao status) {
        return sugestao(status, null);
    }

    private SugestaoCoach sugestao(StatusSugestao status, Instant expiresAt) {
        Atleta atleta = Atleta.builder().id(UUID.randomUUID()).nome("Ana").build();
        return SugestaoCoach.builder()
                .id(sugestaoId)
                .tenantId(tenantId)
                .atleta(atleta)
                .tipo(TipoSugestao.RECOVERY)
                .status(status)
                .confidence("HIGH")
                .summary("Revisar carga")
                .createdAt(Instant.now())
                .expiresAt(expiresAt)
                .build();
    }

    private SugestaoCoachOutputDto outputDto(UUID id) {
        return new SugestaoCoachOutputDto(id, UUID.randomUUID(), "Ana", TipoSugestao.RECOVERY,
                StatusSugestao.PENDING, "HIGH", "Revisar carga", null,
                Instant.now(), null, null, null, null);
    }

    // ── listar ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listar")
    class Listar {

        @Test
        @DisplayName("retorna DTOs para todas as sugestões do status solicitado")
        void retornaDtosDoStatus() {
            SugestaoCoach s = sugestao(StatusSugestao.APPROVED);
            SugestaoCoachOutputDto dto = outputDto(sugestaoId);
            when(repository.findByTenantIdAndStatus(tenantId, StatusSugestao.APPROVED)).thenReturn(List.of(s));
            when(mapper.toOutputDto(s)).thenReturn(dto);

            List<SugestaoCoachOutputDto> result = service.listar(StatusSugestao.APPROVED);

            assertThat(result).containsExactly(dto);
        }

        @Test
        @DisplayName("lista PENDING: exclui sugestões com expiresAt no passado")
        void filtraExpiradas() {
            Instant passado = Instant.now().minus(1, ChronoUnit.HOURS);
            SugestaoCoach expirada = sugestao(StatusSugestao.PENDING, passado);
            when(repository.findByTenantIdAndStatus(tenantId, StatusSugestao.PENDING))
                    .thenReturn(List.of(expirada));

            List<SugestaoCoachOutputDto> result = service.listar(StatusSugestao.PENDING);

            assertThat(result).isEmpty();
            verifyNoInteractions(mapper);
        }

        @Test
        @DisplayName("lista PENDING: mantém sugestão com expiresAt no futuro")
        void mantemNaoExpirada() {
            Instant futuro = Instant.now().plus(1, ChronoUnit.DAYS);
            SugestaoCoach valida = sugestao(StatusSugestao.PENDING, futuro);
            SugestaoCoachOutputDto dto = outputDto(sugestaoId);
            when(repository.findByTenantIdAndStatus(tenantId, StatusSugestao.PENDING))
                    .thenReturn(List.of(valida));
            when(mapper.toOutputDto(valida)).thenReturn(dto);

            List<SugestaoCoachOutputDto> result = service.listar(StatusSugestao.PENDING);

            assertThat(result).containsExactly(dto);
        }

        @Test
        @DisplayName("lista PENDING: mantém sugestão com expiresAt null (sem expiração)")
        void mantemSemExpiracao() {
            SugestaoCoach semExpiracao = sugestao(StatusSugestao.PENDING, null);
            SugestaoCoachOutputDto dto = outputDto(sugestaoId);
            when(repository.findByTenantIdAndStatus(tenantId, StatusSugestao.PENDING))
                    .thenReturn(List.of(semExpiracao));
            when(mapper.toOutputDto(semExpiracao)).thenReturn(dto);

            List<SugestaoCoachOutputDto> result = service.listar(StatusSugestao.PENDING);

            assertThat(result).containsExactly(dto);
        }

        @Test
        @DisplayName("lista vazia quando repositório não retorna resultados")
        void listaVazia() {
            when(repository.findByTenantIdAndStatus(tenantId, StatusSugestao.PENDING))
                    .thenReturn(List.of());

            assertThat(service.listar(StatusSugestao.PENDING)).isEmpty();
        }
    }

    // ── listarPorAtleta ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("listarPorAtleta")
    class ListarPorAtleta {

        private final UUID atletaId = UUID.randomUUID();

        @Test
        @DisplayName("retorna lista mapeada quando existem sugestões para o atleta")
        void retornaListaMapeada() {
            SugestaoCoach s = sugestao(StatusSugestao.PENDING);
            SugestaoCoachOutputDto dto = outputDto(sugestaoId);
            when(repository.findAllByAtletaIdAndTenantId(eq(atletaId), eq(tenantId), any())).thenReturn(List.of(s));
            when(mapper.toOutputDto(s)).thenReturn(dto);

            List<SugestaoCoachOutputDto> result = service.listarPorAtleta(atletaId);

            assertThat(result).containsExactly(dto);
            verify(repository).findAllByAtletaIdAndTenantId(eq(atletaId), eq(tenantId), any());
        }

        @Test
        @DisplayName("retorna lista vazia quando o atleta não possui sugestões")
        void retornaListaVazia() {
            when(repository.findAllByAtletaIdAndTenantId(eq(atletaId), eq(tenantId), any())).thenReturn(List.of());

            List<SugestaoCoachOutputDto> result = service.listarPorAtleta(atletaId);

            assertThat(result).isEmpty();
            verifyNoInteractions(mapper);
        }

        @Test
        @DisplayName("cross-tenant — atletaId de outro tenant retorna vazio (query filtra por tenantId)")
        void crossTenantRetornaVazio() {
            UUID atletaOutroTenant = UUID.randomUUID();
            when(repository.findAllByAtletaIdAndTenantId(eq(atletaOutroTenant), eq(tenantId), any())).thenReturn(List.of());

            List<SugestaoCoachOutputDto> result = service.listarPorAtleta(atletaOutroTenant);

            assertThat(result).isEmpty();
            verify(repository).findAllByAtletaIdAndTenantId(eq(atletaOutroTenant), eq(tenantId), any());
            verifyNoInteractions(mapper);
        }
    }

    // ── detalhe ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("detalhe")
    class Detalhe {

        @Test
        @DisplayName("retorna DTO quando sugestão pertence ao tenant")
        void retornaDto() {
            SugestaoCoach s = sugestao(StatusSugestao.PENDING);
            SugestaoCoachOutputDto dto = outputDto(sugestaoId);
            when(repository.findByIdAndTenantId(sugestaoId, tenantId)).thenReturn(Optional.of(s));
            when(mapper.toOutputDto(s)).thenReturn(dto);

            assertThat(service.detalhe(sugestaoId)).isEqualTo(dto);
        }

        @Test
        @DisplayName("lança DomainNotFoundException quando sugestão não pertence ao tenant")
        void lançaNotFoundCrossTenant() {
            when(repository.findByIdAndTenantId(sugestaoId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.detalhe(sugestaoId))
                    .isInstanceOf(DomainNotFoundException.class)
                    .hasMessageContaining(sugestaoId.toString());
        }
    }

    // ── aprovar ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("aprovar")
    class Aprovar {

        @Test
        @DisplayName("PENDING → APPROVED: grava reviewedBy do security context (nunca do corpo, CA1/CA4), "
                + "limpa rejectionReason e retorna DTO")
        void pendingParaApproved() {
            SugestaoCoach s = sugestao(StatusSugestao.PENDING);
            UUID usuarioId = stubReviewedBy();
            SugestaoCoachOutputDto dto = outputDto(sugestaoId);
            when(repository.findByIdAndTenantId(sugestaoId, tenantId)).thenReturn(Optional.of(s));
            when(repository.decidirSePendente(eq(sugestaoId), eq(tenantId), eq(StatusSugestao.PENDING),
                    eq(StatusSugestao.APPROVED), any(), eq(usuarioId), isNull())).thenReturn(1);
            when(mapper.toOutputDto(s)).thenReturn(dto);

            SugestaoCoachOutputDto result = service.aprovar(sugestaoId);

            assertThat(result).isEqualTo(dto);
            assertThat(s.getStatus()).isEqualTo(StatusSugestao.APPROVED);
            assertThat(s.getReviewedAt()).isNotNull();
            assertThat(s.getReviewedBy()).isEqualTo(usuarioId);
            assertThat(s.getRejectionReason()).isNull();
            verify(repository).decidirSePendente(eq(sugestaoId), eq(tenantId), eq(StatusSugestao.PENDING),
                    eq(StatusSugestao.APPROVED), any(), eq(usuarioId), isNull());
        }

        @Test
        @DisplayName("APPROVED → APPROVED: no-op — decidirSePendente não chamado")
        void reAprovarENoOp() {
            SugestaoCoach s = sugestao(StatusSugestao.APPROVED);
            SugestaoCoachOutputDto dto = outputDto(sugestaoId);
            when(repository.findByIdAndTenantId(sugestaoId, tenantId)).thenReturn(Optional.of(s));
            when(mapper.toOutputDto(s)).thenReturn(dto);

            service.aprovar(sugestaoId);

            verify(repository, never()).decidirSePendente(any(), any(), any(), any(), any(), any(), any());
            verifyNoInteractions(principalResolver);
        }

        @Test
        @DisplayName("REJECTED → APPROVED: lança DomainRuleViolationException (422)")
        void rejectedParaApprovedIlegal() {
            SugestaoCoach s = sugestao(StatusSugestao.REJECTED);
            when(repository.findByIdAndTenantId(sugestaoId, tenantId)).thenReturn(Optional.of(s));

            assertThatThrownBy(() -> service.aprovar(sugestaoId))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("REJECTED");

            verify(repository, never()).decidirSePendente(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("lança DomainNotFoundException quando sugestão não encontrada")
        void lançaNotFound() {
            when(repository.findByIdAndTenantId(sugestaoId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.aprovar(sugestaoId))
                    .isInstanceOf(DomainNotFoundException.class);
        }

        @Test
        @DisplayName("decisão concorrente perde a corrida (0 linhas afetadas): lança DomainConflictException "
                + "(409, CA6) — entidade em memória não é mutada, auditoria da vencedora não é sobrescrita")
        void decisaoConcorrenteLancaConflito() {
            SugestaoCoach s = sugestao(StatusSugestao.PENDING);
            stubReviewedBy();
            when(repository.findByIdAndTenantId(sugestaoId, tenantId)).thenReturn(Optional.of(s));
            when(repository.decidirSePendente(any(), any(), any(), any(), any(), any(), any())).thenReturn(0);

            assertThatThrownBy(() -> service.aprovar(sugestaoId))
                    .isInstanceOf(DomainConflictException.class);

            assertThat(s.getStatus()).isEqualTo(StatusSugestao.PENDING);
            verifyNoInteractions(mapper);
        }
    }

    // ── rejeitar ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rejeitar")
    class Rejeitar {

        @Test
        @DisplayName("PENDING → REJECTED sem corpo: reviewedBy gravado, rejectionReason null (CA3)")
        void pendingParaRejectedSemMotivo() {
            SugestaoCoach s = sugestao(StatusSugestao.PENDING);
            UUID usuarioId = stubReviewedBy();
            SugestaoCoachOutputDto dto = outputDto(sugestaoId);
            when(repository.findByIdAndTenantId(sugestaoId, tenantId)).thenReturn(Optional.of(s));
            when(repository.decidirSePendente(eq(sugestaoId), eq(tenantId), eq(StatusSugestao.PENDING),
                    eq(StatusSugestao.REJECTED), any(), eq(usuarioId), isNull())).thenReturn(1);
            when(mapper.toOutputDto(s)).thenReturn(dto);

            SugestaoCoachOutputDto result = service.rejeitar(sugestaoId, null);

            assertThat(result).isEqualTo(dto);
            assertThat(s.getStatus()).isEqualTo(StatusSugestao.REJECTED);
            assertThat(s.getReviewedAt()).isNotNull();
            assertThat(s.getReviewedBy()).isEqualTo(usuarioId);
            assertThat(s.getRejectionReason()).isNull();
        }

        @Test
        @DisplayName("PENDING → REJECTED com rejectionReason: motivo gravado junto da auditoria (CA2)")
        void pendingParaRejectedComMotivo() {
            SugestaoCoach s = sugestao(StatusSugestao.PENDING);
            UUID usuarioId = stubReviewedBy();
            SugestaoCoachOutputDto dto = outputDto(sugestaoId);
            RejeitarSugestaoRequestDto request = new RejeitarSugestaoRequestDto("volume alto demais para a semana");
            when(repository.findByIdAndTenantId(sugestaoId, tenantId)).thenReturn(Optional.of(s));
            when(repository.decidirSePendente(eq(sugestaoId), eq(tenantId), eq(StatusSugestao.PENDING),
                    eq(StatusSugestao.REJECTED), any(), eq(usuarioId), eq("volume alto demais para a semana")))
                    .thenReturn(1);
            when(mapper.toOutputDto(s)).thenReturn(dto);

            service.rejeitar(sugestaoId, request);

            assertThat(s.getRejectionReason()).isEqualTo("volume alto demais para a semana");
            assertThat(s.getReviewedBy()).isEqualTo(usuarioId);
        }

        @Test
        @DisplayName("REJECTED → REJECTED: no-op — decidirSePendente não chamado")
        void reRejeitarENoOp() {
            SugestaoCoach s = sugestao(StatusSugestao.REJECTED);
            SugestaoCoachOutputDto dto = outputDto(sugestaoId);
            when(repository.findByIdAndTenantId(sugestaoId, tenantId)).thenReturn(Optional.of(s));
            when(mapper.toOutputDto(s)).thenReturn(dto);

            service.rejeitar(sugestaoId, null);

            verify(repository, never()).decidirSePendente(any(), any(), any(), any(), any(), any(), any());
            verifyNoInteractions(principalResolver);
        }

        @Test
        @DisplayName("APPROVED → REJECTED: lança DomainRuleViolationException (422)")
        void approvedParaRejectedIlegal() {
            SugestaoCoach s = sugestao(StatusSugestao.APPROVED);
            when(repository.findByIdAndTenantId(sugestaoId, tenantId)).thenReturn(Optional.of(s));

            assertThatThrownBy(() -> service.rejeitar(sugestaoId, null))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("APPROVED");

            verify(repository, never()).decidirSePendente(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("lança DomainNotFoundException quando sugestão não encontrada")
        void lançaNotFound() {
            when(repository.findByIdAndTenantId(sugestaoId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.rejeitar(sugestaoId, null))
                    .isInstanceOf(DomainNotFoundException.class);
        }

        @Test
        @DisplayName("decisão concorrente perde a corrida (0 linhas afetadas): lança DomainConflictException (409, CA6)")
        void decisaoConcorrenteLancaConflito() {
            SugestaoCoach s = sugestao(StatusSugestao.PENDING);
            stubReviewedBy();
            when(repository.findByIdAndTenantId(sugestaoId, tenantId)).thenReturn(Optional.of(s));
            when(repository.decidirSePendente(any(), any(), any(), any(), any(), any(), any())).thenReturn(0);

            assertThatThrownBy(() -> service.rejeitar(sugestaoId, null))
                    .isInstanceOf(DomainConflictException.class);

            assertThat(s.getStatus()).isEqualTo(StatusSugestao.PENDING);
            verifyNoInteractions(mapper);
        }
    }
}
