package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.ConfiancaInferencia;

public record ThresholdEstimate<T extends Number>(T valor, int amostras, ConfiancaInferencia confianca) {}
