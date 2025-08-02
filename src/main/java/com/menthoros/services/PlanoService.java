package com.menthoros.services;

import com.menthoros.dto.PlanoDto;

import java.util.UUID;

public interface PlanoService {
    PlanoDto gerarPlanoTreino(UUID atletaId);
}
