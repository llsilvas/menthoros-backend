package br.com.menthoros.backend.exception;

/**
 * Coach tentou uma operação de escrita sem ter aceitado as versões vigentes dos Termos e da
 * Política de Privacidade.
 *
 * <p>Mapeada para {@code 403 LGPD_CONSENT_REQUIRED}. O código próprio existe para o frontend
 * distinguir isto de um 403 de autorização: aqui a ação é exibir o modal de consentimento, não
 * dizer ao usuário que ele não tem permissão.
 */
public class LgpdConsentRequiredException extends RuntimeException {
    public LgpdConsentRequiredException(String message) {
        super(message);
    }
}
