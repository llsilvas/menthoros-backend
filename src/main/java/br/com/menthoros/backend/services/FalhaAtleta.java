package br.com.menthoros.backend.services;

import java.util.UUID;

/**
 * Falha no encerramento da semana de um atleta durante o lote (não aborta o lote).
 *
 * @param atletaId atleta cujo encerramento falhou
 * @param motivo   descrição do erro
 */
public record FalhaAtleta(UUID atletaId, String motivo) {
}
