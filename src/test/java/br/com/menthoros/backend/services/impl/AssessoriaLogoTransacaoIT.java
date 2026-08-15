package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaLogoRepository;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.services.AssessoriaLogoService;
import br.com.menthoros.backend.services.helper.TenantCoerenciaGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Prova que o upload da logo é atômico contra o Postgres real.
 *
 * <p><b>Deliberadamente SEM {@code @Transactional} na classe.</b> O rollback automático que o Spring
 * aplica a testes transacionais envolveria a transação do serviço na do teste — e então tudo
 * "reverteria" no fim, inclusive um upload que tivesse sido commitado indevidamente. O teste
 * passaria sem provar nada. Aqui cada chamada de serviço abre e fecha a própria transação, e o
 * estado é lido depois, como a aplicação leria. O preço é limpar a massa à mão no {@code @AfterEach}.
 *
 * <p>A falha é injetada no <b>bump de versão da assessoria</b>, que acontece <i>depois</i> de a logo
 * ser gravada — é o ponto onde uma implementação não-atômica deixaria bytes órfãos.
 */
@DisplayName("Upload da logo — atomicidade contra Postgres real")
class AssessoriaLogoTransacaoIT extends AbstractIntegrationTest {

    @Autowired private AssessoriaLogoService logoService;
    @Autowired private AssessoriaLogoRepository logoRepository;

    @MockitoSpyBean private AssessoriaRepository assessoriaRepository;
    /**
     * O guard resolve o usuário do JWT; sem requisição HTTP não há principal. Substituí-lo mantém
     * o teste focado na transação — a autorização é exercitada no {@code *ControllerIT}.
     */
    @MockitoBean private TenantCoerenciaGuard tenantCoerenciaGuard;

    private UUID tenantId;

    @BeforeEach
    void preparar() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Atomicidade");
        assessoria.setDominio("atomicidade-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        tenantId = assessoriaRepository.save(assessoria).getId();

        TenantContext.setTenantId(tenantId);
        when(tenantCoerenciaGuard.exigirCoerencia()).thenReturn(tenantId);
    }

    @AfterEach
    void limpar() {
        // `reset` em vez de `doCallRealMethod`: em spy de repositório Spring Data o segundo não
        // restaura o comportamento e a limpeza morre com erro do próprio Mockito.
        Mockito.reset(assessoriaRepository);
        logoRepository.findById(tenantId).ifPresent(logoRepository::delete);
        assessoriaRepository.findById(tenantId).ifPresent(assessoriaRepository::delete);
        TenantContext.clear();
    }

    @Test
    @DisplayName("falha após gravar os bytes não deixa linha órfã")
    void falhaNoBumpNaoDeixaOrfao() throws IOException {
        long versao = versaoAtual();
        doThrow(new IllegalStateException("falha injetada no bump de versão"))
                .when(assessoriaRepository).saveAndFlush(any());

        // O tipo não é o injetado: o proxy do Spring Data traduz exceções do repositório
        // (aqui vira InvalidDataAccessApiUsageException). A mensagem é o que identifica a injeção.
        assertThatThrownBy(() -> logoService.substituir(png(64, 64), versao))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("falha injetada");

        assertThat(logoRepository.existsByAssessoriaId(tenantId))
                .as("os bytes não podem sobreviver ao fracasso da operação que os acompanhava")
                .isFalse();
    }

    /**
     * O caso que importa para quem já tem logo: uma substituição que falha no meio não pode deixar
     * a assessoria pior do que estava. Perder a logo antiga por causa de um upload que nem
     * completou seria uma regressão silenciosa — o coach só descobriria ao olhar a página.
     */
    @Test
    @DisplayName("falha na substituição preserva a logo anterior intacta")
    void falhaNaSubstituicaoPreservaAnterior() throws IOException {
        logoService.substituir(png(32, 32), versaoAtual());
        String etagOriginal = logoRepository.findEtagByAssessoriaId(tenantId).orElseThrow();

        doThrow(new IllegalStateException("falha injetada no bump de versão"))
                .when(assessoriaRepository).saveAndFlush(any());

        assertThatThrownBy(() -> logoService.substituir(png(128, 128), versaoAtual()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("falha injetada");

        Mockito.reset(assessoriaRepository);
        assertThat(logoRepository.findEtagByAssessoriaId(tenantId))
                .as("a logo anterior tem de continuar exatamente como estava")
                .contains(etagOriginal);
    }

    @Test
    @DisplayName("upload bem-sucedido persiste e incrementa a versão")
    void uploadBemSucedidoPersiste() throws IOException {
        long antes = versaoAtual();

        logoService.substituir(png(48, 48), antes);

        assertThat(logoRepository.existsByAssessoriaId(tenantId)).isTrue();
        assertThat(versaoAtual())
                .as("a versão precisa avançar, senão duas abas nunca colidem")
                .isGreaterThan(antes);
    }

    /**
     * Sem esta garantia o `304` seria impossível: o cliente guarda o ETag e precisa de um valor
     * novo assim que o conteúdo muda, ou passa a servir imagem velha do cache indefinidamente.
     */
    @Test
    @DisplayName("substituir a logo troca o ETag")
    void substituicaoTrocaEtag() throws IOException {
        logoService.substituir(png(32, 32), versaoAtual());
        String primeiro = logoRepository.findEtagByAssessoriaId(tenantId).orElseThrow();

        logoService.substituir(png(96, 96), versaoAtual());
        String segundo = logoRepository.findEtagByAssessoriaId(tenantId).orElseThrow();

        assertThat(segundo).isNotEqualTo(primeiro);
        // Uma linha POR ASSESSORIA — contar a tabela inteira acusaria massa de outros testes,
        // que sem `@Transactional` de fato sobrevive.
        assertThat(logoRepository.findById(tenantId)).isPresent();
    }

    private long versaoAtual() {
        return assessoriaRepository.findById(tenantId).orElseThrow().getVersion();
    }

    private byte[] png(int largura, int altura) throws IOException {
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(imagem, "png", saida);
        return saida.toByteArray();
    }
}
