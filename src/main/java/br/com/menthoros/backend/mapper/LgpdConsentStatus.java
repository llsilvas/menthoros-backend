package br.com.menthoros.backend.mapper;

import java.time.Instant;

/**
 * Estado de consentimento LGPD agrupado para entrada do {@link UsuarioMapper}.
 *
 * <p>Não é DTO de resposta — os campos seguem <b>planos</b> no {@code UsuarioMeOutputDto}, porque
 * aninhá-los agora quebraria o contrato que o frontend já consome. Este tipo existe só para a
 * assinatura do mapper: sem ele, {@code toMeOutputDto} iria a nove parâmetros posicionais, e o Data
 * Clump nessa assinatura já tinha sido apontado na revisão da change anterior.
 *
 * <p><b>Vigente e aceito são coisas diferentes.</b> {@code current*} vem da configuração e é o que o
 * cliente ecoa ao registrar o aceite; {@code accepted*} vem do último registro em
 * {@code tb_usuario_lgpd_consent} e pode ser mais antigo. Quando divergem, {@code granted} é
 * {@code false} — houve bump de versão — e é essa diferença que a tela de privacidade mostra.
 *
 * @param granted              já aceitou as versões vigentes?
 * @param currentPolicyVersion versão da Política em vigor (config)
 * @param currentTermsVersion  versão dos Termos em vigor (config)
 * @param consentedAt          momento do último aceite; {@code null} se nunca consentiu
 * @param acceptedPolicyVersion versão da Política do último aceite; {@code null} se nunca consentiu
 * @param acceptedTermsVersion  versão dos Termos do último aceite; {@code null} se nunca consentiu
 */
public record LgpdConsentStatus(
        boolean granted,
        String currentPolicyVersion,
        String currentTermsVersion,
        Instant consentedAt,
        String acceptedPolicyVersion,
        String acceptedTermsVersion) {
}
