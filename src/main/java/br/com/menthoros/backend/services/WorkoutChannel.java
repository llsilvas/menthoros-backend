package br.com.menthoros.backend.services;

import br.com.menthoros.backend.domain.workout.StructuredWorkout;
import br.com.menthoros.backend.entity.IntegracaoExterna;

import java.time.LocalDate;
import java.util.Set;

/**
 * Canal de entrega de um {@link StructuredWorkout} a uma plataforma externa. Contrato único que
 * todo adapter concreto implementa — o chamador (listener/scheduler) não conhece o formato ou o
 * protocolo do canal.
 */
public interface WorkoutChannel {

    /**
     * Envia (cria ou atualiza) um treino estruturado no canal externo.
     *
     * @param conexao           credencial e identificador do atleta na plataforma externa
     * @param workout            treino estruturado a enviar
     * @param eventIdArmazenado id do evento já criado no canal (null no primeiro push)
     * @return resultado do push — nunca lança, erros viram {@link PushResult#erro}
     */
    PushResult push(IntegracaoExterna conexao, StructuredWorkout workout, Long eventIdArmazenado);

    /**
     * Remove, na janela informada, os eventos criados pelo Menthoros que não correspondem a
     * nenhum treino atual — reconciliação após re-aprovação de plano. Eventos criados pelo
     * próprio atleta (sem o prefixo do canal) nunca são tocados.
     *
     * @param conexao            credencial e identificador do atleta na plataforma externa
     * @param inicio             início da janela (inclusive)
     * @param fim                fim da janela (inclusive)
     * @param externalIdsAtuais external_id de todos os treinos atuais do plano nessa janela
     */
    void removerOrfaos(IntegracaoExterna conexao, LocalDate inicio, LocalDate fim, Set<String> externalIdsAtuais);

    /**
     * "Cutuca" (nudge) um evento já criado no canal externo para contornar o debounce do uploader
     * Garmin do intervals.icu: eventos CRIADOS em rajada (2+ no mesmo lote) podem não disparar o
     * upload real do treino estruturado ao dispositivo Garmin do atleta, mesmo com o evento salvo
     * corretamente. Reenviar o {@code external_id} com o MESMO valor é um PUT parcial — no-op do
     * ponto de vista do dado — que reabre a janela do uploader e faz o disparo acontecer
     * (comprovado empiricamente; ver {@code IntervalsIcuPushListener}, que só chama este método no
     * ÚLTIMO evento criado de um lote com 2+ criações).
     *
     * @param conexao            credencial e identificador do atleta na plataforma externa
     * @param eventId            id do evento já criado no canal a ser "cutucado"
     * @param externalIdCanonico external_id do evento — reenviado com o valor canônico atual
     */
    void tocarEvento(IntegracaoExterna conexao, long eventId, String externalIdCanonico);
}
