package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.services.EmbeddingService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingServiceImpl(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public List<Float> gerarEmbedding(String texto) {
        var embeddingRequest = new EmbeddingRequest(List.of(texto), null);
        EmbeddingResponse response = embeddingModel.call(embeddingRequest);

        float[] output = response.getResults().get(0).getOutput();

        List<Float> embedding = new ArrayList<>(output.length);

        for (float f : output) {
            embedding.add(f);
        }

        return embedding;
    }
}
