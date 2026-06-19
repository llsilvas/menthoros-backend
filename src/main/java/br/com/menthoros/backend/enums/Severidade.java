package br.com.menthoros.backend.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Severidade de um item da fila de atenção do treinador.
 *
 * <p>O {@code peso} ordena severidades (maior = mais grave) e é usado tanto na ordenação da fila
 * quanto no corte de exibição da v1 (apenas {@code peso} de ALTA/CRITICA são exibidos).
 */
@Getter
@RequiredArgsConstructor
public enum Severidade {

    CRITICA(3),
    ALTA(2),
    MEDIA(1);

    private final int peso;
}
