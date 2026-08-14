package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.AssessoriaPatchInputDto;
import br.com.menthoros.backend.dto.output.AssessoriaMeOutputDto;

/**
 * Configuração da assessoria pelo próprio coach — distinto de {@link AssessoriaService}, que serve
 * ao cadastro administrativo de tenants.
 */
public interface AssessoriaSettingsService {

    /**
     * Devolve a assessoria do tenant corrente com plano, uso e versão.
     *
     * <p><b>Idempotent:</b> YES — leitura pura.
     * <p><b>Side Effects:</b> NONE
     * <p><b>Tenant-aware:</b> YES — resolve por {@code TenantContext.getRequiredTenantId()}.
     *
     * @return configuração da assessoria do tenant corrente
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o tenant não existir
     */
    AssessoriaMeOutputDto buscarDoTenantCorrente();

    /**
     * Atualiza os campos editáveis da assessoria do tenant corrente.
     *
     * <p><b>Idempotent:</b> NO — cada chamada bem-sucedida incrementa a versão, então repetir a
     * mesma requisição com a versão original devolve {@code 409}. Isso é o comportamento desejado:
     * a repetição costuma ser uma segunda aba, não um retry.
     * <p><b>Side Effects:</b> Database update em {@code tb_assessoria}.
     * <p><b>Tenant-aware:</b> YES — resolve por {@code TenantContext.getRequiredTenantId()}.
     *
     * @param input campos editáveis e a versão lida no GET
     * @return a assessoria já atualizada, com a versão nova
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o tenant não existir
     * @throws jakarta.persistence.OptimisticLockException se a versão informada estiver obsoleta
     */
    AssessoriaMeOutputDto atualizarDoTenantCorrente(AssessoriaPatchInputDto input);
}
