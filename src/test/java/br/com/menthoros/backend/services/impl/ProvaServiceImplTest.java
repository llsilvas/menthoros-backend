package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.ProvaInputDto;
import br.com.menthoros.backend.dto.output.ProvaOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.TipoProva;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.mapper.ProvaMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProvaServiceImplTest {

    @Mock
    private ProvaRepository provaRepository;
    @Mock
    private AtletaRepository atletaRepository;
    @Mock
    private AssessoriaRepository assessoriaRepository;
    @Mock
    private ProvaMapper provaMapper;

    @InjectMocks
    private ProvaServiceImpl provaService;

    private UUID tenantId;
    private UUID atletaId;
    private UUID provaId;
    private Assessoria assessoria;
    private Atleta atleta;
    private Prova prova;
    private ProvaInputDto inputDto;
    private ProvaOutputDto outputDto;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        provaId = UUID.randomUUID();

        assessoria = new Assessoria();
        assessoria.setId(tenantId);

        atleta = Atleta.builder()
                .id(atletaId)
                .assessoria(assessoria)
                .build();

        prova = Prova.builder()
                .id(provaId)
                .nomeProva("Maratona SP")
                .dataProva(LocalDate.now().plusDays(60))
                .distancia(DistanciaProva.KM_42)
                .statusProva(ProvaStatus.PLANEJADA)
                .foiRealizada(false)
                .atleta(atleta)
                .build();

        inputDto = new ProvaInputDto(
                "Maratona SP",
                LocalDate.now().plusDays(60),
                TipoProva.MARATONA,
                DistanciaProva.KM_42,
                null, false, null, null, null, null, null,
                null, null, null, null, null, null, null, null
        );

        outputDto = new ProvaOutputDto(
                provaId, "Maratona SP", LocalDate.now().plusDays(60),
                TipoProva.MARATONA, DistanciaProva.KM_42, null, false, ProvaStatus.PLANEJADA,
                null, null, null, false, null, null, null, null, null,
                null, null, null, 60
        );
    }

    @Test
    @DisplayName("criarProva deve criar e retornar prova quando atleta pertence ao tenant")
    void criarProva_deveRetornarProvaOutputDto_quandoAtletaPertenceAoTenant() {
        try (MockedStatic<TenantContext> tenantCtx = mockStatic(TenantContext.class)) {
            tenantCtx.when(TenantContext::hasTenant).thenReturn(true);
            tenantCtx.when(TenantContext::getTenantId).thenReturn(tenantId);

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(provaMapper.toEntity(inputDto)).thenReturn(prova);
            when(provaRepository.save(prova)).thenReturn(prova);
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            ProvaOutputDto result = provaService.criarProva(atletaId, inputDto);

            assertThat(result).isNotNull();
            assertThat(result.nomeProva()).isEqualTo("Maratona SP");
            verify(provaRepository).save(prova);
        }
    }

    @Test
    @DisplayName("listarProvas deve retornar lista de provas do atleta ordenada por data")
    void listarProvas_deveRetornarLista_quandoAtletaValido() {
        try (MockedStatic<TenantContext> tenantCtx = mockStatic(TenantContext.class)) {
            tenantCtx.when(TenantContext::hasTenant).thenReturn(true);
            tenantCtx.when(TenantContext::getTenantId).thenReturn(tenantId);

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(provaRepository.findByAtletaOrderByDataProvaAsc(atleta)).thenReturn(List.of(prova));
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            List<ProvaOutputDto> result = provaService.listarProvas(atletaId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).nomeProva()).isEqualTo("Maratona SP");
        }
    }

    @Test
    @DisplayName("listarProvas deve retornar lista vazia quando atleta não tem provas")
    void listarProvas_deveRetornarListaVazia_quandoAtletaSemProvas() {
        try (MockedStatic<TenantContext> tenantCtx = mockStatic(TenantContext.class)) {
            tenantCtx.when(TenantContext::hasTenant).thenReturn(true);
            tenantCtx.when(TenantContext::getTenantId).thenReturn(tenantId);

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(provaRepository.findByAtletaOrderByDataProvaAsc(atleta)).thenReturn(List.of());

            List<ProvaOutputDto> result = provaService.listarProvas(atletaId);

            assertThat(result).isEmpty();
        }
    }

    @Test
    @DisplayName("buscarProvaPorId deve retornar prova quando pertence ao atleta")
    void buscarProvaPorId_deveRetornarProva_quandoProvaDoAtleta() {
        try (MockedStatic<TenantContext> tenantCtx = mockStatic(TenantContext.class)) {
            tenantCtx.when(TenantContext::hasTenant).thenReturn(true);
            tenantCtx.when(TenantContext::getTenantId).thenReturn(tenantId);

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(provaRepository.findById(provaId)).thenReturn(Optional.of(prova));
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            ProvaOutputDto result = provaService.buscarProvaPorId(atletaId, provaId);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(provaId);
        }
    }

    @Test
    @DisplayName("atualizarProva deve atualizar e retornar prova atualizada")
    void atualizarProva_deveRetornarProvaAtualizada() {
        try (MockedStatic<TenantContext> tenantCtx = mockStatic(TenantContext.class)) {
            tenantCtx.when(TenantContext::hasTenant).thenReturn(true);
            tenantCtx.when(TenantContext::getTenantId).thenReturn(tenantId);

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(provaRepository.findById(provaId)).thenReturn(Optional.of(prova));
            when(provaRepository.save(prova)).thenReturn(prova);
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            ProvaOutputDto result = provaService.atualizarProva(atletaId, provaId, inputDto);

            assertThat(result).isNotNull();
            verify(provaMapper).updateEntity(inputDto, prova);
            verify(provaRepository).save(prova);
        }
    }

    @Test
    @DisplayName("deletarProva deve deletar prova quando pertence ao atleta")
    void deletarProva_deveDeletar_quandoProvaDoAtleta() {
        try (MockedStatic<TenantContext> tenantCtx = mockStatic(TenantContext.class)) {
            tenantCtx.when(TenantContext::hasTenant).thenReturn(true);
            tenantCtx.when(TenantContext::getTenantId).thenReturn(tenantId);

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(provaRepository.findById(provaId)).thenReturn(Optional.of(prova));

            provaService.deletarProva(atletaId, provaId);

            verify(provaRepository).delete(prova);
        }
    }

    @Test
    @DisplayName("criarProva deve lançar ResourceNotFoundException quando atleta é de outro tenant")
    void criarProva_deveLancarException_quandoAtletaDeOutroTenant() {
        try (MockedStatic<TenantContext> tenantCtx = mockStatic(TenantContext.class)) {
            tenantCtx.when(TenantContext::hasTenant).thenReturn(true);
            tenantCtx.when(TenantContext::getTenantId).thenReturn(tenantId);

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> provaService.criarProva(atletaId, inputDto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(atletaId.toString());

            verify(provaRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("buscarProvaPorId deve lançar ResourceNotFoundException quando prova não pertence ao atleta")
    void buscarProvaPorId_deveLancarException_quandoProvaNaoPertenceAoAtleta() {
        UUID outroAtletaId = UUID.randomUUID();
        Atleta outroAtleta = Atleta.builder().id(outroAtletaId).assessoria(assessoria).build();
        Prova provaDeOutroAtleta = Prova.builder()
                .id(provaId)
                .atleta(outroAtleta)
                .build();

        try (MockedStatic<TenantContext> tenantCtx = mockStatic(TenantContext.class)) {
            tenantCtx.when(TenantContext::hasTenant).thenReturn(true);
            tenantCtx.when(TenantContext::getTenantId).thenReturn(tenantId);

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(provaRepository.findById(provaId)).thenReturn(Optional.of(provaDeOutroAtleta));

            assertThatThrownBy(() -> provaService.buscarProvaPorId(atletaId, provaId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(provaId.toString());
        }
    }
}
