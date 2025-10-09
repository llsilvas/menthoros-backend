package com.menthoros.services;


import com.menthoros.entity.PlanoSemanal;
import com.menthoros.enums.ModoGeracaoPlano;
import jakarta.transaction.Transactional;

import java.util.UUID;

public interface PlanoService {
    @Transactional
    PlanoSemanal gerarPlanoTreino(UUID atletaId, ModoGeracaoPlano modoGeracao);

    void deletePlanoSemanal(UUID planoSemanalId);
}
