package com.menthoros.services;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.menthoros.entity.PlanoSemanal;

import java.util.UUID;

public interface PlanoService {
    PlanoSemanal gerarPlanoTreino(UUID atletaId) throws JsonProcessingException;

    void deletePlanoSemanal(UUID planoSemanalId);
}
