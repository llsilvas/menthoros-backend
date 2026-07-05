package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.fit.FitSessionData;
import br.com.menthoros.backend.exception.FitParseException;

import java.io.InputStream;

public interface FitParseService {

    /**
     * Idempotent: YES — leitura pura do stream, sem efeitos colaterais. Side Effects: NONE.
     * Tenant-aware: NÃO SE APLICA (não acessa dado do banco).
     *
     * @param in stream do arquivo .fit enviado
     * @return dados de sessão e laps extraídos
     * @throws FitParseException se o arquivo não for um .fit válido ou não contiver uma
     *         mensagem Session
     */
    FitSessionData parse(InputStream in);
}
