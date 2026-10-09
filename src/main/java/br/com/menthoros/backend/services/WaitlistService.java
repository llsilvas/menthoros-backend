package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.WaitlistInputDto;

public interface WaitlistService {

    enum Resultado { CRIADO, JA_INSCRITO, IGNORADO }

    /**
     * Inscreve um interessado na waitlist pública (pré-signup, sem tenant).
     *
     * <p><b>Idempotent:</b> YES — e-mail duplicado (mesmo após normalização) não cria nova linha;
     * retorna {@code JA_INSCRITO}, inclusive sob corrida (resolvida pelo índice único).
     * <p><b>Side Effects:</b> Database insert quando o e-mail é novo. Quando já existe, faz um
     * update de contato/atribuição (nome, telefone, qtdAtletas, watchBrand, landingPath,
     * referrer) — <b>nunca</b> de {@code perfil}, {@code aceiteLgpd}, {@code policyVersion} ou
     * UTM, que ficam congelados no valor da primeira inscrição (endpoint público, sem
     * verificação de posse do e-mail). Honeypot acionado não persiste.
     * <p><b>Tenant-aware:</b> NO — cadastro global, pré-signup, não usa {@code TenantContext}.
     *
     * @param dto dados validados da inscrição (honeypot em {@code website})
     * @return {@code CRIADO} (nova inscrição), {@code JA_INSCRITO} (e-mail já existente) ou
     *         {@code IGNORADO} (honeypot acionado)
     */
    Resultado registrar(WaitlistInputDto dto);
}
