package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.AtletaInputDto;
import br.com.menthoros.backend.dto.output.AtletaOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.mapper.AtletaMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.services.KeycloakOrganizationGateway;
import br.com.menthoros.backend.services.TsbService;
import org.springframework.data.jpa.domain.Specification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AtletaServiceImpl - Testes unitários")
class AtletaServiceImplTest {

    @Mock
    private AtletaRepository atletaRepository;

    @Mock
    private AssessoriaRepository assessoriaRepository;

    @Mock
    private AtletaMapper atletaMapper;

    @Mock
    private PlanoMetadadosRepository planoMetadadosRepository;

    @Mock
    private TsbService tsbService;

    @Mock
    private KeycloakOrganizationGateway keycloakOrganizationGateway;

    @InjectMocks
    private AtletaServiceImpl atletaService;

    private UUID tenantId;
    private UUID atletaId;
    private Assessoria assessoria;
    private Atleta atletaEntity;
    private AtletaInputDto atletaInputDto;
    private AtletaOutputDto atletaOutputDto;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();

        // Setup assessoria
        assessoria = new Assessoria();
        assessoria.setId(tenantId);
        assessoria.setAtivo(true);

        // Setup atleta entity
        atletaEntity = new Atleta();
        atletaEntity.setId(atletaId);
        atletaEntity.setNome("João Silva");
        atletaEntity.setDataNascimento(LocalDate.of(1993, 5, 15));
        atletaEntity.setPesoKg(BigDecimal.valueOf(75.0));
        atletaEntity.setAlturaCm(BigDecimal.valueOf(180.0));
        atletaEntity.setObjetivo("Completar maratona em menos de 4 horas");
        atletaEntity.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atletaEntity.setDiasDisponiveis(List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SEXTA));
        atletaEntity.setDiaPreferidoLongo(DiaSemana.SABADO);
        atletaEntity.setTemLesao(false);
        atletaEntity.setAtivo(AtletaStatus.ATIVO);
        atletaEntity.setAssessoria(assessoria);

        // Setup DTOs
        atletaInputDto = new AtletaInputDto(
                "João Silva",
                "1993-05-15",
                BigDecimal.valueOf(75.0),
                BigDecimal.valueOf(180.0),
                "Completar maratona em menos de 4 horas",
                NivelExperiencia.INTERMEDIARIO,
                Set.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SEXTA),
                DiaSemana.SABADO,
                false,
                null,
                null,
                null
        );

        atletaOutputDto = new AtletaOutputDto(
                atletaId,
                "João Silva",
                30,
                BigDecimal.valueOf(75.0),
                BigDecimal.valueOf(180.0),
                "Completar maratona em menos de 4 horas",
                NivelExperiencia.INTERMEDIARIO,
                Set.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SEXTA),
                DiaSemana.SABADO,
                false,
                null,
                List.of(),
                null,
                null,
                null
        );

        // Setup TenantContext
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("createAtleta deve criar um novo atleta e retornar a entidade salva")
    void testCreateAtletaSuccess() {
        when(assessoriaRepository.findById(tenantId)).thenReturn(Optional.of(assessoria));
        when(atletaMapper.toEntity(atletaInputDto)).thenReturn(new Atleta());
        when(atletaRepository.save(any(Atleta.class))).thenReturn(atletaEntity);

        Atleta result = atletaService.createAtleta(atletaInputDto);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(atletaId);
        assertThat(result.getNome()).isEqualTo("João Silva");
        assertThat(result.getAtivo()).isEqualTo(AtletaStatus.ATIVO);

        verify(assessoriaRepository).findById(tenantId);
        verify(atletaRepository).save(any(Atleta.class));
    }

    @Test
    @DisplayName("createAtleta deve lançar exceção quando assessoria não existe")
    void testCreateAtletaAssessoriaNotFound() {
        when(assessoriaRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> atletaService.createAtleta(atletaInputDto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Assessoria não encontrada");

        verify(atletaRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateAtleta deve atualizar dados de um atleta existente")
    void testUpdateAtletaSuccess() {
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atletaEntity));
        when(atletaRepository.save(any(Atleta.class))).thenReturn(atletaEntity);
        when(atletaMapper.toOutputDto(atletaEntity)).thenReturn(atletaOutputDto);

        AtletaOutputDto result = atletaService.updateAtleta(atletaId, atletaInputDto);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(atletaId);

        verify(atletaRepository).findByIdAndTenantId(atletaId, tenantId);
        verify(atletaMapper).updateEntity(atletaInputDto, atletaEntity);
        verify(atletaRepository).save(atletaEntity);
    }

    @Test
    @DisplayName("updateAtleta deve lançar exceção quando atleta não existe")
    void testUpdateAtletaNotFound() {
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> atletaService.updateAtleta(atletaId, atletaInputDto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Atleta não encontrado");

        verify(atletaRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteAtleta deve marcar atleta como inativo (soft delete)")
    void testDeleteAtletaSuccess() {
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atletaEntity));
        when(atletaRepository.save(any(Atleta.class))).thenReturn(atletaEntity);

        atletaService.deleteAtleta(atletaId);

        assertThat(atletaEntity.getAtivo()).isEqualTo(AtletaStatus.INATIVO);
        verify(atletaRepository).findByIdAndTenantId(atletaId, tenantId);
        verify(atletaRepository).save(atletaEntity);
    }

    @Test
    @DisplayName("deleteAtleta deve lançar exceção quando atleta não existe")
    void testDeleteAtletaNotFound() {
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> atletaService.deleteAtleta(atletaId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Atleta não encontrado");

        verify(atletaRepository, never()).save(any());
    }

    @Test
    @DisplayName("getAtletaById deve retornar DTO do atleta quando existe")
    void testGetAtletaByIdSuccess() {
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atletaEntity));
        when(atletaMapper.toOutputDto(atletaEntity)).thenReturn(atletaOutputDto);

        AtletaOutputDto result = atletaService.getAtletaById(atletaId);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(atletaId);
        assertThat(result.nome()).isEqualTo("João Silva");

        verify(atletaRepository).findByIdAndTenantId(atletaId, tenantId);
        verify(atletaMapper).toOutputDto(atletaEntity);
    }

    @Test
    @DisplayName("getAtletaById deve lançar exceção quando atleta não existe")
    void testGetAtletaByIdNotFound() {
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> atletaService.getAtletaById(atletaId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Atleta não encontrado");
    }

    @Test
    @DisplayName("getAllAtletas deve retornar lista de atletas do tenant sem filtros")
    void testGetAllAtletasSuccess() {
        when(atletaRepository.findAll(any(Specification.class))).thenReturn(List.of(atletaEntity));
        when(atletaMapper.toOutputDto(any(Atleta.class))).thenReturn(atletaOutputDto);

        List<AtletaOutputDto> result = atletaService.getAllAtletas(null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(atletaId);
        verify(atletaRepository).findAll(any(Specification.class));
        verify(atletaMapper).toOutputDto(atletaEntity);
    }

    @Test
    @DisplayName("getAllAtletas deve retornar lista vazia quando não há atletas")
    void testGetAllAtletasEmpty() {
        when(atletaRepository.findAll(any(Specification.class))).thenReturn(List.of());

        List<AtletaOutputDto> result = atletaService.getAllAtletas(null, null, null);

        assertThat(result).isEmpty();
        verify(atletaRepository).findAll(any(Specification.class));
    }

    @Test
    @DisplayName("recalcularMetricasAtleta deve delegar para TsbService")
    void testRecalcularMetricasAtleta() {
        atletaService.recalcularMetricasAtleta(atletaId);

        verify(tsbService).recalcularHistoricoCompleto(atletaId);
    }

    @Test
    @DisplayName("createAtleta lança IllegalStateException quando TenantContext vazio (sem fallback)")
    void testCreateAtletaWithoutTenantThrowsIllegalState() {
        TenantContext.clear();

        assertThatThrownBy(() -> atletaService.createAtleta(atletaInputDto))
                .isInstanceOf(IllegalStateException.class);

        // Garante que o fallback para a primeira assessoria NÃO é mais chamado
        verify(assessoriaRepository, never()).findFirstByAtivoTrue();
        verify(atletaRepository, never()).save(any());
    }
}
