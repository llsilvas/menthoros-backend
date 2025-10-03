package com.menthoros.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StatusSincronizacao {

    // ===== ESTADOS INICIAIS =====
    NAO_SINCRONIZADO("Não sincronizado", "Treino ainda não foi enviado para plataforma externa"),

    PENDENTE("Pendente", "Aguardando sincronização"),

    // ===== ESTADOS EM PROCESSO =====
    SINCRONIZANDO("Sincronizando", "Sincronização em andamento"),

    AGUARDANDO_RETRY("Aguardando retry", "Aguardando nova tentativa após erro temporário"),

    // ===== ESTADOS DE SUCESSO =====
    SINCRONIZADO("Sincronizado", "Sincronizado com sucesso"),

    SINCRONIZADO_PARCIAL("Sincronizado parcialmente", "Sincronizado em algumas plataformas, mas não em todas"),

    // ===== ESTADOS DE ERRO =====
    ERRO_TEMPORARIO("Erro temporário", "Erro temporário, será reprocessado automaticamente"),

    ERRO_AUTENTICACAO("Erro de autenticação", "Token expirado ou inválido - requer reautenticação do usuário"),

    ERRO_VALIDACAO("Erro de validação", "Dados do treino inválidos para a plataforma externa"),

    ERRO_LIMITE_RATE("Limite de requisições", "Limite de requisições da API atingido"),

    ERRO_PERMANENTE("Erro permanente", "Erro que não será reprocessado automaticamente"),

    // ===== ESTADOS ESPECIAIS =====
    DESABILITADO("Desabilitado", "Sincronização desabilitada pelo usuário"),

    CONFLITO("Conflito detectado", "Dados conflitantes entre sistema local e plataforma externa"),

    CANCELADO("Cancelado", "Sincronização cancelada pelo usuário ou sistema");

    private final String descricao;
    private final String detalhe;

    // ===== MÉTODOS DE CONVENIÊNCIA =====

    public boolean podeTentarNovamente() {
        return this == ERRO_TEMPORARIO ||
                this == ERRO_LIMITE_RATE ||
                this == AGUARDANDO_RETRY;
    }

    public boolean precisaIntervencaoUsuario() {
        return this == ERRO_AUTENTICACAO ||
                this == CONFLITO ||
                this == ERRO_VALIDACAO;
    }

    public boolean estaSincronizado() {
        return this == SINCRONIZADO ||
                this == SINCRONIZADO_PARCIAL;
    }

    public boolean emProcesso() {
        return this == PENDENTE ||
                this == SINCRONIZANDO ||
                this == AGUARDANDO_RETRY;
    }

    public boolean temErro() {
        return name().startsWith("ERRO_");
    }
}