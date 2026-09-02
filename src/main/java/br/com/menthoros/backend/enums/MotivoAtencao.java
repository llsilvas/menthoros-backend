package br.com.menthoros.backend.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Motivo principal pelo qual um atleta entra na fila de atenção do treinador.
 *
 * <p>Cada motivo carrega um {@code peso} (desempate de prioridade dentro da mesma severidade) e um
 * {@code suggestedAction} determinístico — texto acionável apresentado ao treinador (v1 sem IA).
 */
@Getter
@RequiredArgsConstructor
public enum MotivoAtencao {

    FADIGA(50, "Revisar carga: reduzir volume/intensidade ou inserir recuperação até o TSB normalizar."),
    PROVA_ATLETA(45, "Revise a prova e o plano das próximas semanas."),
    SOBRECARGA(40, "Reduzir a progressão da semana ou inserir recuperação ativa; evitar novo aumento de carga."),
    SEM_PLANO(35, "Atleta sem plano ativo: gerar ou ativar um plano de treino."),
    ADERENCIA(30, "Falar com o atleta sobre os treinos perdidos e ajustar o plano à rotina real."),
    INATIVIDADE(20, "Contatar o atleta: sem atividade na janela; verificar status e engajamento."),
    ZONAS_VENCIDAS(10, "Reagendar teste de FC/pace: zonas com 3+ meses podem prescrever mal.");

    private final int peso;
    private final String suggestedAction;
}
