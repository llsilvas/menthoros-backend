package br.com.menthoros.backend.exception;

/**
 * Não foi possível resolver o usuário para avaliar o consentimento — atributo ausente, de tipo
 * inesperado, ou com tenant divergente do contexto.
 *
 * <p>Mapeada para {@code 503}, deliberadamente <b>não</b> {@code 403}. "Não consegui verificar" e
 * "não consentiu" são causas diferentes: tratar a primeira como a segunda mandaria o coach para o
 * modal de consentimento por causa de uma falha de infraestrutura, e esconderia o defeito real
 * atrás de uma tela de produto.
 */
public class ConsentResolutionUnavailableException extends RuntimeException {
    public ConsentResolutionUnavailableException(String message) {
        super(message);
    }
}
