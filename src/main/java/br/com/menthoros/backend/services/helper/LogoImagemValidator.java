package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.exception.DomainRuleViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decide o que é uma imagem aceitável para logo — decodificando os bytes, nunca acreditando no que
 * o cliente diz que enviou.
 *
 * <p>Extensão e {@code Content-Type} são strings escolhidas por quem faz o upload: um executável
 * renomeado para {@code .png} chega com {@code image/png} se o cliente quiser. A única verificação
 * que vale é tentar decodificar de fato.
 */
@Slf4j
@Component
public class LogoImagemValidator {

    /** 2 MiB. Casa com o CHECK de {@code tb_assessoria_logo.size_bytes}. */
    public static final int TAMANHO_MAXIMO_BYTES = 2 * 1024 * 1024;
    public static final int DIMENSAO_MAXIMA_PX = 2048;

    /**
     * Formatos que o {@link ImageIO} do JDK decodifica sem plugin externo.
     *
     * <p><b>WebP ficou de fora</b>: o JDK não traz reader para ele, e suportá-lo exigiria uma
     * dependência nova (TwelveMonkeys ou equivalente) — decisão de escopo, não limitação técnica
     * permanente. SVG segue fora por outro motivo: pode carregar script.
     */
    private static final Map<String, String> FORMATOS_ACEITOS = Map.of(
            "png", "image/png",
            "jpeg", "image/jpeg",
            "jpg", "image/jpeg");

    private static final Set<String> EXTENSOES_DESCRITIVAS = Set.of("PNG", "JPEG", "JPG");

    /**
     * Valida e descreve a imagem.
     *
     * @param bytes conteúdo cru do upload
     * @return formato detectado a partir do próprio conteúdo
     * @throws DomainRuleViolationException se estiver vazia, grande demais, não for imagem
     *                                      decodificável, tiver formato não aceito ou exceder as
     *                                      dimensões máximas
     */
    public LogoValidada validar(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new DomainRuleViolationException("Arquivo de logo vazio");
        }
        if (bytes.length > TAMANHO_MAXIMO_BYTES) {
            throw new DomainRuleViolationException(
                    "Logo excede o limite de 2 MiB (recebido: " + bytes.length + " bytes)");
        }

        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (entrada == null) {
                throw new DomainRuleViolationException("Não foi possível ler o arquivo enviado");
            }

            Iterator<ImageReader> leitores = ImageIO.getImageReaders(entrada);
            if (!leitores.hasNext()) {
                throw new DomainRuleViolationException(
                        "O arquivo enviado não é uma imagem válida. Formatos aceitos: "
                                + EXTENSOES_DESCRITIVAS);
            }

            ImageReader leitor = leitores.next();
            try {
                leitor.setInput(entrada);
                String formato = leitor.getFormatName().toLowerCase(Locale.ROOT);

                String contentType = FORMATOS_ACEITOS.get(formato);
                if (contentType == null) {
                    throw new DomainRuleViolationException(
                            "Formato de imagem não aceito: " + formato + ". Aceitos: "
                                    + EXTENSOES_DESCRITIVAS);
                }

                // Decodificar de fato, não só ler o cabeçalho: um arquivo com header de PNG e corpo
                // truncado passa pela detecção de formato e falha aqui, que é onde deve falhar.
                BufferedImage imagem = leitor.read(0);
                if (imagem == null) {
                    throw new DomainRuleViolationException("Imagem corrompida ou ilegível");
                }
                if (imagem.getWidth() > DIMENSAO_MAXIMA_PX || imagem.getHeight() > DIMENSAO_MAXIMA_PX) {
                    throw new DomainRuleViolationException(
                            "Logo excede %dx%d px (recebida: %dx%d)".formatted(
                                    DIMENSAO_MAXIMA_PX, DIMENSAO_MAXIMA_PX,
                                    imagem.getWidth(), imagem.getHeight()));
                }

                return new LogoValidada(contentType, bytes.length, hash(bytes));
            } finally {
                leitor.dispose();
            }
        } catch (IOException e) {
            log.warn("Falha ao decodificar imagem de logo: {}", e.getMessage());
            throw new DomainRuleViolationException("Não foi possível processar a imagem enviada");
        }
    }

    /** SHA-256 em hexadecimal — 64 caracteres, exatamente o tamanho da coluna {@code etag}. */
    private String hash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }

    /**
     * @param contentType tipo derivado do conteúdo real, nunca do cabeçalho do cliente
     * @param tamanhoBytes tamanho conferido
     * @param etag hash do conteúdo, usado para {@code 304}
     */
    public record LogoValidada(String contentType, int tamanhoBytes, String etag) {}
}
