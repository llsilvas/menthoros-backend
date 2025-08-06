package com.menthoros.services;


import com.menthoros.entity.PlanoSemanal;

import java.util.UUID;

public interface PlanoService {
    PlanoSemanal gerarPlanoTreino(UUID atletaId);
}
