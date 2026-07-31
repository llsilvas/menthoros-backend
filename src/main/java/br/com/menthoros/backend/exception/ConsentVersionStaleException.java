package br.com.menthoros.backend.exception;

/**
 * O cliente tentou registrar aceite declarando uma versão de documento que não é mais a vigente.
 *
 * <p>Mapeada para {@code 409 CONSENT_VERSION_STALE}. Não é erro do usuário: é o caso de a Política
 * ou os Termos terem mudado enquanto a página estava aberta. Gravar assim mesmo produziria um
 * registro afirmando que ele aceitou um texto que nunca viu, então o aceite é recusado e o
 * frontend recarrega o conteúdo atualizado.
 */
public class ConsentVersionStaleException extends RuntimeException {
    public ConsentVersionStaleException(String message) {
        super(message);
    }
}
