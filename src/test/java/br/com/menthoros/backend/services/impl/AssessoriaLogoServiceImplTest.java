package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.AssessoriaLogo;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaLogoRepository;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.services.AssessoriaLogoService;
import br.com.menthoros.backend.services.AssessoriaSettingsService;
import br.com.menthoros.backend.services.helper.LogoImagemValidator;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessoriaLogoServiceImplTest {

    @Mock private AssessoriaRepository assessoriaRepository;
    @Mock private AssessoriaLogoRepository logoRepository;
    @Mock private AssessoriaSettingsService settingsService;

    private AssessoriaLogoServiceImpl service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        // O validador é lógica pura e determinística — usar o real torna o teste honesto sobre o
        // que de fato é aceito, em vez de assumir que o mock aprovou.
        service = new AssessoriaLogoServiceImpl(
                assessoriaRepository, logoRepository, new LogoImagemValidator(), settingsService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("substituir")
    class Substituir {

        @Test
        @DisplayName("persiste bytes, tipo derivado do conteúdo e etag")
        void persisteLogo() throws IOException {
            stubAssessoria(3L);
            when(logoRepository.findById(tenantId)).thenReturn(Optional.empty());
            when(settingsService.buscarDoTenantCorrente()).thenReturn(mock(
                    br.com.menthoros.backend.dto.output.AssessoriaMeOutputDto.class));

            service.substituir(png(64, 64), 3L);

            ArgumentCaptor<AssessoriaLogo> captor = ArgumentCaptor.forClass(AssessoriaLogo.class);
            verify(logoRepository).save(captor.capture());

            AssessoriaLogo salva = captor.getValue();
            assertThat(salva.getAssessoriaId()).isEqualTo(tenantId);
            assertThat(salva.getContentType()).isEqualTo("image/png");
            assertThat(salva.getEtag()).hasSize(64);
            assertThat(salva.getSizeBytes()).isPositive();
        }

        @Test
        @DisplayName("substituir atualiza a linha existente, não cria outra")
        void substituiLinhaExistente() throws IOException {
            stubAssessoria(3L);
            AssessoriaLogo existente = AssessoriaLogo.builder()
                    .assessoriaId(tenantId)
                    .content("antigo".getBytes(StandardCharsets.UTF_8))
                    .contentType("image/jpeg")
                    .sizeBytes(6)
                    .etag("etag-antigo")
                    .updatedAt(OffsetDateTime.now())
                    .build();
            when(logoRepository.findById(tenantId)).thenReturn(Optional.of(existente));
            when(settingsService.buscarDoTenantCorrente()).thenReturn(mock(
                    br.com.menthoros.backend.dto.output.AssessoriaMeOutputDto.class));

            service.substituir(png(32, 32), 3L);

            assertThat(existente.getContentType()).isEqualTo("image/png");
            assertThat(existente.getEtag()).isNotEqualTo("etag-antigo");
            verify(logoRepository).save(existente);
        }

        @Test
        @DisplayName("incrementa a versão da assessoria — a logo mora em outra tabela")
        void tocaAssessoria() throws IOException {
            Assessoria assessoria = stubAssessoria(3L);
            when(logoRepository.findById(tenantId)).thenReturn(Optional.empty());
            when(settingsService.buscarDoTenantCorrente()).thenReturn(mock(
                    br.com.menthoros.backend.dto.output.AssessoriaMeOutputDto.class));

            service.substituir(png(16, 16), 3L);

            verify(assessoriaRepository).saveAndFlush(assessoria);
        }

        /**
         * Validar antes de escrever não é ordem arbitrária: um arquivo recusado não pode consumir a
         * versão da assessoria nem deixar linha pela metade.
         */
        @Test
        @DisplayName("arquivo inválido não escreve nada")
        void arquivoInvalidoNaoEscreve() {
            stubAssessoria(3L);

            assertThatThrownBy(() -> service.substituir(
                    "nao sou uma imagem".getBytes(StandardCharsets.UTF_8), 3L))
                    .isInstanceOf(DomainRuleViolationException.class);

            verify(logoRepository, never()).save(any());
            verify(assessoriaRepository, never()).saveAndFlush(any());
            verifyNoInteractions(settingsService);
        }

        @Test
        @DisplayName("versão obsoleta não chega a validar o arquivo")
        void versaoObsoleta() throws IOException {
            stubAssessoria(4L);

            assertThatThrownBy(() -> service.substituir(png(16, 16), 3L))
                    .isInstanceOf(OptimisticLockException.class);

            verify(logoRepository, never()).save(any());
            verify(assessoriaRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("tenant sem assessoria vira 404 sem escrever")
        void tenantInexistente() throws IOException {
            when(assessoriaRepository.findById(tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.substituir(png(16, 16), 3L))
                    .isInstanceOf(DomainNotFoundException.class);

            verify(logoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("buscar")
    class Buscar {

        @Test
        @DisplayName("devolve conteúdo, tipo e etag quando existe")
        void devolveLogo() {
            when(logoRepository.findById(tenantId)).thenReturn(Optional.of(AssessoriaLogo.builder()
                    .assessoriaId(tenantId)
                    .content(new byte[]{1, 2, 3})
                    .contentType("image/png")
                    .sizeBytes(3)
                    .etag("abc")
                    .updatedAt(OffsetDateTime.now())
                    .build()));

            Optional<AssessoriaLogoService.LogoBinario> logo = service.buscar();

            assertThat(logo).isPresent();
            assertThat(logo.get().contentType()).isEqualTo("image/png");
            assertThat(logo.get().etag()).isEqualTo("abc");
            assertThat(logo.get().conteudo()).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("vazio quando a assessoria não tem logo")
        void semLogo() {
            when(logoRepository.findById(tenantId)).thenReturn(Optional.empty());

            assertThat(service.buscar()).isEmpty();
        }

        @Test
        @DisplayName("buscarEtag não carrega o conteúdo")
        void etagSemConteudo() {
            when(logoRepository.findEtagByAssessoriaId(tenantId)).thenReturn(Optional.of("hash"));

            assertThat(service.buscarEtag()).contains("hash");
            verify(logoRepository, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("remover")
    class Remover {

        @Test
        @DisplayName("apaga a linha e incrementa a versão")
        void removeLogo() {
            Assessoria assessoria = stubAssessoria(3L);

            service.remover(3L);

            verify(logoRepository).deleteById(tenantId);
            verify(assessoriaRepository).saveAndFlush(assessoria);
        }

        /**
         * O caso que o DELETE sem versão deixaria passar: uma aba antiga apagando a logo que outra
         * acabou de enviar.
         */
        @Test
        @DisplayName("versão obsoleta não apaga nada")
        void versaoObsoleta() {
            stubAssessoria(4L);

            assertThatThrownBy(() -> service.remover(3L))
                    .isInstanceOf(OptimisticLockException.class);

            verify(logoRepository, never()).deleteById(any());
            verify(assessoriaRepository, never()).saveAndFlush(any());
        }
    }

    private Assessoria stubAssessoria(Long version) {
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        assessoria.setNome("Corridas Serra");
        assessoria.setVersion(version);
        when(assessoriaRepository.findById(tenantId)).thenReturn(Optional.of(assessoria));
        return assessoria;
    }

    private byte[] png(int largura, int altura) throws IOException {
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(imagem, "png", saida);
        return saida.toByteArray();
    }
}
