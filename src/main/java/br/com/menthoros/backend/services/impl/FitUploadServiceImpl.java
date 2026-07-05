package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.fit.FitImportResultado;
import br.com.menthoros.backend.dto.fit.FitSessionData;
import br.com.menthoros.backend.services.FitParseService;
import br.com.menthoros.backend.services.FitUploadService;
import br.com.menthoros.backend.services.helper.FitTreinoPersister;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FitUploadServiceImpl implements FitUploadService {

    private final FitParseService fitParseService;
    private final FitTreinoPersister fitTreinoPersister;

    @Override
    public FitImportResultado importar(UUID atletaId, InputStream in) {
        // Parse do binário (CPU/IO-bound, sem acesso a banco) fica FORA da transação — só
        // FitTreinoPersister.persistir (declarativamente @Transactional) precisa segurar uma
        // conexão, e só pelo tempo da persistência.
        FitSessionData dados = fitParseService.parse(in);
        return fitTreinoPersister.persistir(atletaId, dados);
    }
}
