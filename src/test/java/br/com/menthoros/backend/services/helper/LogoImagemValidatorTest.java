package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.exception.DomainRuleViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LogoImagemValidator")
class LogoImagemValidatorTest {

    private final LogoImagemValidator validator = new LogoImagemValidator();

    @Nested
    @DisplayName("imagens aceitas")
    class ImagensAceitas {

        @Test
        @DisplayName("PNG válido devolve content-type derivado do conteúdo")
        void pngValido() throws IOException {
            LogoImagemValidator.LogoValidada resultado = validator.validar(png(64, 64));

            assertThat(resultado.contentType()).isEqualTo("image/png");
            assertThat(resultado.tamanhoBytes()).isPositive();
            assertThat(resultado.etag()).hasSize(64);
        }

        @Test
        @DisplayName("JPEG válido é aceito")
        void jpegValido() throws IOException {
            assertThat(validator.validar(jpeg(64, 64)).contentType()).isEqualTo("image/jpeg");
        }

        @Test
        @DisplayName("o etag é do conteúdo: imagens idênticas geram o mesmo hash")
        void etagDeterministico() throws IOException {
            byte[] imagem = png(32, 32);

            assertThat(validator.validar(imagem).etag())
                    .isEqualTo(validator.validar(imagem.clone()).etag());
        }

        @Test
        @DisplayName("imagens diferentes geram etags diferentes")
        void etagDistingueConteudo() throws IOException {
            assertThat(validator.validar(png(32, 32)).etag())
                    .isNotEqualTo(validator.validar(png(48, 48)).etag());
        }

        @Test
        @DisplayName("exatamente no limite de dimensão é aceito")
        void limiteDeDimensaoInclusivo() throws IOException {
            assertThat(validator.validar(png(2048, 8))).isNotNull();
        }
    }

    @Nested
    @DisplayName("rejeições")
    class Rejeicoes {

        @ParameterizedTest
        @NullSource
        @DisplayName("nulo é rejeitado")
        void nulo(byte[] bytes) {
            assertThatThrownBy(() -> validator.validar(bytes))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("vazio");
        }

        @Test
        @DisplayName("vazio é rejeitado")
        void vazio() {
            assertThatThrownBy(() -> validator.validar(new byte[0]))
                    .isInstanceOf(DomainRuleViolationException.class);
        }

        /**
         * O caso que motiva o validador: extensão e Content-Type são escolhidos por quem envia.
         */
        @ParameterizedTest
        @ValueSource(strings = {
                "isto e apenas um texto, nao uma imagem",
                "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>",
                "%PDF-1.4 conteudo de pdf"
        })
        @DisplayName("arquivo que não é imagem é rejeitado, mesmo com nome de imagem")
        void naoImagem(String conteudo) {
            assertThatThrownBy(() -> validator.validar(conteudo.getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(DomainRuleViolationException.class);
        }

        @Test
        @DisplayName("acima de 2 MiB é rejeitado antes de tentar decodificar")
        void acimaDoLimite() {
            byte[] gigante = new byte[LogoImagemValidator.TAMANHO_MAXIMO_BYTES + 1];

            assertThatThrownBy(() -> validator.validar(gigante))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("2 MiB");
        }

        @Test
        @DisplayName("PNG com cabeçalho válido e corpo truncado é rejeitado")
        void pngTruncado() throws IOException {
            byte[] completo = png(256, 256);
            byte[] truncado = Arrays.copyOf(completo, completo.length / 3);

            assertThatThrownBy(() -> validator.validar(truncado))
                    .isInstanceOf(DomainRuleViolationException.class);
        }

        @Test
        @DisplayName("imagem acima das dimensões máximas é rejeitada")
        void dimensoesExcedidas() throws IOException {
            assertThatThrownBy(() -> validator.validar(png(2049, 8)))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("2048");
        }

        /**
         * GIF decodifica pelo ImageIO, então passa pela detecção de formato — e ainda assim não
         * está na lista de aceitos. Prova que a validação não é só "conseguiu decodificar".
         */
        @Test
        @DisplayName("formato decodificável mas não aceito (GIF) é rejeitado")
        void formatoForaDaLista() throws IOException {
            assertThatThrownBy(() -> validator.validar(imagem("gif", 32, 32)))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("não aceito");
        }
    }

    private byte[] png(int largura, int altura) throws IOException {
        return imagem("png", largura, altura);
    }

    private byte[] jpeg(int largura, int altura) throws IOException {
        return imagem("jpeg", largura, altura);
    }

    private byte[] imagem(String formato, int largura, int altura) throws IOException {
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics();
        g.setColor(new Color(largura % 255, altura % 255, 128));
        g.fillRect(0, 0, largura, altura);
        g.dispose();

        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(imagem, formato, saida);
        return saida.toByteArray();
    }
}
