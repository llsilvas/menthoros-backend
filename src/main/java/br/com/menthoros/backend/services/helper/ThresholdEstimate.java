package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.ConfiancaInferencia;

public record ThresholdEstimate(Number valor, int amostras, ConfiancaInferencia confianca) {}
