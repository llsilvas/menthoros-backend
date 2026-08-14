package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.AssessoriaMeOutputDto;

import java.util.Optional;

/**
 * Logo da assessoria do tenant corrente.
 *
 * <p>Separado de {@link AssessoriaSettingsService} porque tem colaboradores próprios (validação de
 * imagem, repositório de bytes) e um ciclo de vida distinto do resto da configuração.
 */
public interface AssessoriaLogoService {

    /**
     * Substitui a logo da assessoria do tenant corrente.
     *
     * <p><b>Idempotent:</b> NO — cada envio bem-sucedido incrementa a versão da assessoria, então
     * repetir com a versão original devolve {@code 409}.
     * <p><b>Side Effects:</b> Database upsert em {@code tb_assessoria_logo} + bump de versão em
     * {@code tb_assessoria}, na mesma transação.
     * <p><b>Tenant-aware:</b> YES
     *
     * @param bytes conteúdo cru do upload, validado por decode antes de qualquer escrita
     * @param version versão lida no GET
     * @return a configuração já atualizada
     * @throws br.com.menthoros.backend.exception.DomainRuleViolationException se o arquivo não for
     *                                      imagem aceita, exceder 2 MiB ou as dimensões máximas
     * @throws jakarta.persistence.OptimisticLockException se a versão estiver obsoleta
     */
    AssessoriaMeOutputDto substituir(byte[] bytes, Long version);

    /**
     * Lê a logo para servir.
     *
     * <p><b>Idempotent:</b> YES — leitura pura.
     * <p><b>Side Effects:</b> NONE
     * <p><b>Tenant-aware:</b> YES
     *
     * @return vazio quando a assessoria não tem logo
     */
    Optional<LogoBinario> buscar();

    /**
     * Lê apenas o {@code ETag}, sem trazer os bytes — para responder {@code 304}.
     *
     * <p><b>Idempotent:</b> YES · <b>Side Effects:</b> NONE · <b>Tenant-aware:</b> YES
     */
    Optional<String> buscarEtag();

    /**
     * Remove a logo da assessoria do tenant corrente.
     *
     * <p><b>Idempotent:</b> NO — incrementa a versão, como qualquer escrita. Remover uma logo que
     * já não existe é no-op quanto aos bytes, mas ainda assim exige versão atual.
     * <p><b>Side Effects:</b> Database delete + bump de versão.
     * <p><b>Tenant-aware:</b> YES
     *
     * @throws jakarta.persistence.OptimisticLockException se a versão estiver obsoleta
     */
    void remover(Long version);

    /**
     * @param conteudo bytes da imagem
     * @param contentType tipo derivado do conteúdo no momento do upload
     * @param etag hash do conteúdo
     */
    record LogoBinario(byte[] conteudo, String contentType, String etag) {}
}
