package br.com.menthoros.backend.services.email;

/**
 * Mensagem transacional pronta para envio. Corpo em HTML com alternativa em texto puro.
 *
 * <p>O {@code toString()} é mascarado: o corpo pode carregar um link com segredo (token de
 * convite), e o record padrão o imprimiria em qualquer log acidental.</p>
 */
public record EmailMessage(String to, String subject, String html, String text) {

    public EmailMessage {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Destinatário do e-mail é obrigatório");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Assunto do e-mail é obrigatório");
        }
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("Corpo HTML do e-mail é obrigatório");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Corpo em texto do e-mail é obrigatório");
        }
        // Cabeçalhos são linhas: CR/LF em destinatário ou assunto é header injection, e nenhum
        // validador do chamador (@Email, constante) pode ser a única barreira.
        if (temQuebraDeLinha(to) || temQuebraDeLinha(subject)) {
            throw new IllegalArgumentException("Destinatário e assunto não podem conter quebra de linha");
        }
    }

    private static boolean temQuebraDeLinha(String valor) {
        return valor.indexOf('\r') >= 0 || valor.indexOf('\n') >= 0;
    }

    @Override
    public String toString() {
        return "EmailMessage[to=%s, subject=%s, body=***]".formatted(to, subject);
    }
}
