package br.com.menthoros.backend.services;

import java.util.List;

public interface EmbeddingService {
    public List<Float> gerarEmbedding(String texto);
}
