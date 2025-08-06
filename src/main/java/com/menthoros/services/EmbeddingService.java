package com.menthoros.services;

import java.util.List;

public interface EmbeddingService {
    public List<Float> gerarEmbedding(String texto);
}
