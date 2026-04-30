package br.com.menthoros.backend.services;


import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.enums.ModoGeracaoPlano;
import jakarta.transaction.Transactional;

import java.util.UUID;

public interface PlanoService {
    @Transactional
    PlanoSemanal gerarPlanoTreino(UUID atletaId, ModoGeracaoPlano modoGeracao);

    void deletePlanoSemanal(UUID planoSemanalId);
}
