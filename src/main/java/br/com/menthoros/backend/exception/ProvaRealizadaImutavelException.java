package br.com.menthoros.backend.exception;

/**
 * O atleta tentou alterar ou cancelar uma prova já realizada. Especialização de
 * {@link DomainConflictException}, portanto responde HTTP 409 pelo handler já existente.
 */
public class ProvaRealizadaImutavelException extends DomainConflictException {
    public ProvaRealizadaImutavelException(String message) {
        super(message);
    }
}
