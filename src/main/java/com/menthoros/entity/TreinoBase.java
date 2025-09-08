package com.menthoros.entity;

import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.TipoTreino;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@MappedSuperclass
@Getter
@Setter
public abstract class TreinoBase {

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false)
    protected DiaSemana diaSemana;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_treino", nullable = false)
    protected TipoTreino tipoTreino;

    @Column(name = "descricao", nullable = true, length = 1000)
    protected String descricao;

    @Column(name = "fc_alvo")
    protected String fcAlvo; // ex: "140-155"

    @Column(name = "duracao_min")
    protected Integer duracaoMin;

    @Column(name = "distancia_km", precision = 10, scale = 3)
    protected BigDecimal distanciaKm;

    @Column(name = "ritmo_alvo")
    protected String ritmoAlvo;

    @Column(name = "observacao", length = 1000)
    protected String observacao;

}
