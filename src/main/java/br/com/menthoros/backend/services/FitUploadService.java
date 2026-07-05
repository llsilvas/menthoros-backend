package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.fit.FitImportResultado;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.FitParseException;

import java.io.InputStream;
import java.util.UUID;

public interface FitUploadService {

    /**
     * Importa um arquivo .fit para o atleta, persistindo como {@code TreinoRealizado}.
     *
     * Idempotent: YES — reenviar o mesmo .fit (mesmo externalId) retorna o registro já
     * existente em vez de duplicar.
     * Side Effects: Database insert (novo treino + etapas) quando ainda não existe; nenhum
     * quando já existe (apenas leitura).
     * Tenant-aware: YES.
     *
     * @param atletaId atleta autenticado (já resolvido do JWT pelo controller)
     * @param in       stream do arquivo .fit
     * @return resultado com o treino (novo ou já existente) e a flag indicando qual dos dois
     * @throws FitParseException      se o arquivo não for um .fit válido
     * @throws DomainNotFoundException se o atleta não for encontrado no tenant atual
     */
    FitImportResultado importar(UUID atletaId, InputStream in);
}
