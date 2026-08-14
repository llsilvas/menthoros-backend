package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.AssessoriaPatchInputDto;
import br.com.menthoros.backend.dto.output.AssessoriaMeOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import jakarta.persistence.OptimisticLockException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaLogoRepository;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessoriaSettingsServiceImplTest {

    @Mock private AssessoriaRepository assessoriaRepository;
    @Mock private AssessoriaLogoRepository logoRepository;
    @Mock private AtletaRepository atletaRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks private AssessoriaSettingsServiceImpl service;

    private UUID tenantId;

    @BeforeEach
    void setUpTenant() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDownTenant() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("buscarDoTenantCorrente")
    class BuscarDoTenantCorrente {

        @Test
        @DisplayName("monta identidade, plano, uso e versão do tenant corrente")
        void montaSaidaCompleta() {
            stubAssessoria(10, 1);
            when(logoRepository.existsByAssessoriaId(tenantId)).thenReturn(true);
            when(atletaRepository.countAtivosByTenantId(tenantId)).thenReturn(7L);
            when(usuarioRepository.countByTenantIdAndRoleAndAtivoTrue(tenantId, UserRole.TECNICO))
                    .thenReturn(1L);

            AssessoriaMeOutputDto saida = service.buscarDoTenantCorrente();

            assertThat(saida.id()).isEqualTo(tenantId);
            assertThat(saida.nome()).isEqualTo("Corridas Serra");
            assertThat(saida.plano()).isEqualTo(PlanoAssessoria.BASIC);
            assertThat(saida.version()).isEqualTo(3L);
            assertThat(saida.uso().atletas()).isEqualTo(7L);
            assertThat(saida.uso().maxAtletas()).isEqualTo(10);
            assertThat(saida.uso().tecnicos()).isEqualTo(1L);
            assertThat(saida.uso().maxTecnicos()).isEqualTo(1);
        }

        @Test
        @DisplayName("com logo: logoUrl é rota do próprio produto")
        void comLogoDevolveRotaInterna() {
            stubAssessoria(10, 1);
            when(logoRepository.existsByAssessoriaId(tenantId)).thenReturn(true);
            when(atletaRepository.countAtivosByTenantId(tenantId)).thenReturn(0L);
            when(usuarioRepository.countByTenantIdAndRoleAndAtivoTrue(tenantId, UserRole.TECNICO))
                    .thenReturn(0L);

            AssessoriaMeOutputDto saida = service.buscarDoTenantCorrente();

            assertThat(saida.temLogo()).isTrue();
            assertThat(saida.logoUrl())
                    .isEqualTo(AssessoriaSettingsServiceImpl.LOGO_PATH)
                    .doesNotStartWith("http");
        }

        @Test
        @DisplayName("sem logo: temLogo false e logoUrl nulo")
        void semLogoNaoInventaUrl() {
            stubAssessoria(10, 1);
            when(logoRepository.existsByAssessoriaId(tenantId)).thenReturn(false);
            when(atletaRepository.countAtivosByTenantId(tenantId)).thenReturn(0L);
            when(usuarioRepository.countByTenantIdAndRoleAndAtivoTrue(tenantId, UserRole.TECNICO))
                    .thenReturn(0L);

            AssessoriaMeOutputDto saida = service.buscarDoTenantCorrente();

            assertThat(saida.temLogo()).isFalse();
            assertThat(saida.logoUrl()).isNull();
        }

        /**
         * O dono permanece {@code role = TECNICO}; se algum dia for resolvido como
         * {@code PROPRIETARIO}, a contagem exibida aqui passa a divergir do plano.
         */
        @Test
        @DisplayName("a contagem de técnicos usa a role TECNICO, onde o dono também está")
        void contaTecnicosPelaRoleTecnico() {
            stubAssessoria(10, 2);
            when(logoRepository.existsByAssessoriaId(tenantId)).thenReturn(false);
            when(atletaRepository.countAtivosByTenantId(tenantId)).thenReturn(3L);
            when(usuarioRepository.countByTenantIdAndRoleAndAtivoTrue(tenantId, UserRole.TECNICO))
                    .thenReturn(2L);

            AssessoriaMeOutputDto saida = service.buscarDoTenantCorrente();

            assertThat(saida.uso().tecnicos()).isEqualTo(2L);
            verify(usuarioRepository).countByTenantIdAndRoleAndAtivoTrue(tenantId, UserRole.TECNICO);
        }

        @Test
        @DisplayName("tenant inexistente vira DomainNotFoundException sem contar nada")
        void tenantInexistente() {
            when(assessoriaRepository.findById(tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.buscarDoTenantCorrente())
                    .isInstanceOf(DomainNotFoundException.class)
                    .hasMessageContaining("Assessoria não encontrada");

            verifyNoInteractions(atletaRepository, usuarioRepository, logoRepository);
        }

        @Test
        @DisplayName("sem tenant no contexto, falha antes de tocar o banco")
        void semTenantNoContexto() {
            TenantContext.clear();

            assertThatThrownBy(() -> service.buscarDoTenantCorrente())
                    .isInstanceOf(IllegalStateException.class);

            verifyNoInteractions(assessoriaRepository, atletaRepository, usuarioRepository);
        }
    }

    @Nested
    @DisplayName("atualizarDoTenantCorrente")
    class AtualizarDoTenantCorrente {

        @Test
        @DisplayName("altera o nome e devolve a assessoria atualizada")
        void alteraNome() {
            Assessoria assessoria = stubAssessoria(10, 1);
            stubContagens();

            AssessoriaMeOutputDto saida = service.atualizarDoTenantCorrente(
                    new AssessoriaPatchInputDto("Corridas Serra Pro", 3L));

            assertThat(assessoria.getNome()).isEqualTo("Corridas Serra Pro");
            assertThat(saida.nome()).isEqualTo("Corridas Serra Pro");
            verify(assessoriaRepository).save(assessoria);
        }

        @Test
        @DisplayName("normaliza espaços do nome antes de persistir")
        void normalizaNome() {
            Assessoria assessoria = stubAssessoria(10, 1);
            stubContagens();

            service.atualizarDoTenantCorrente(
                    new AssessoriaPatchInputDto("  Corridas   Serra  Pro ", 3L));

            assertThat(assessoria.getNome()).isEqualTo("Corridas Serra Pro");
        }

        /**
         * Duas abas: a primeira salvou e levou a versão a 4; a segunda ainda acredita em 3.
         * Sem esta checagem, a segunda sobrescreveria a primeira em silêncio.
         */
        @Test
        @DisplayName("versão obsoleta vira OptimisticLockException sem salvar")
        void versaoObsoleta() {
            stubAssessoria(10, 1);

            assertThatThrownBy(() -> service.atualizarDoTenantCorrente(
                    new AssessoriaPatchInputDto("Nome novo", 2L)))
                    .isInstanceOf(OptimisticLockException.class);

            verify(assessoriaRepository, never()).save(any());
        }

        @Test
        @DisplayName("tenant inexistente vira DomainNotFoundException sem salvar")
        void tenantInexistente() {
            when(assessoriaRepository.findById(tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.atualizarDoTenantCorrente(
                    new AssessoriaPatchInputDto("Nome novo", 3L)))
                    .isInstanceOf(DomainNotFoundException.class);

            verify(assessoriaRepository, never()).save(any());
        }

        @Test
        @DisplayName("nome em branco é rejeitado mesmo passando pelo Bean Validation do controller")
        void nomeEmBrancoRejeitadoNoServico() {
            stubAssessoria(10, 1);

            assertThatThrownBy(() -> service.atualizarDoTenantCorrente(
                    new AssessoriaPatchInputDto("   ", 3L)))
                    .isInstanceOf(DomainRuleViolationException.class);

            verify(assessoriaRepository, never()).save(any());
        }

        private void stubContagens() {
            when(logoRepository.existsByAssessoriaId(tenantId)).thenReturn(false);
            when(atletaRepository.countAtivosByTenantId(tenantId)).thenReturn(0L);
            when(usuarioRepository.countByTenantIdAndRoleAndAtivoTrue(tenantId, UserRole.TECNICO))
                    .thenReturn(1L);
        }
    }

    private Assessoria stubAssessoria(Integer maxAtletas, Integer maxTecnicos) {
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        assessoria.setNome("Corridas Serra");
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria.setMaxAtletas(maxAtletas);
        assessoria.setMaxTecnicos(maxTecnicos);
        assessoria.setVersion(3L);
        when(assessoriaRepository.findById(tenantId)).thenReturn(Optional.of(assessoria));
        return assessoria;
    }
}
