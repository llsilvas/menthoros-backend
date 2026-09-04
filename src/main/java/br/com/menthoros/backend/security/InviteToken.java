package br.com.menthoros.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Token de convite: segredo de uso único entregue por e-mail.
 *
 * <p>O valor em claro existe apenas no link do e-mail. No banco fica só o {@link #hash()}, que é
 * a chave de busca — vazamento do banco não entrega links válidos. O {@code toString()} é
 * mascarado porque o record padrão imprimiria o segredo em qualquer log acidental.</p>
 *
 * @param value 32 bytes de {@link SecureRandom} em base64url sem padding (43 caracteres)
 */
public record InviteToken(String value) {

    private static final int TAMANHO_EM_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    public InviteToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Token de convite não pode ser vazio");
        }
    }

    public static InviteToken generate() {
        byte[] bytes = new byte[TAMANHO_EM_BYTES];
        RANDOM.nextBytes(bytes);
        return new InviteToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /** SHA-256 do valor, em hex minúsculo — o que é persistido e usado no lookup. */
    public String hash() {
        return hashOf(value);
    }

    public static String hashOf(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Token de convite não pode ser vazio");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }

    @Override
    public String toString() {
        return "InviteToken[***]";
    }
}
