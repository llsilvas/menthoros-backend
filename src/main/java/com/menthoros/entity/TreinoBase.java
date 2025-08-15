package com.menthoros.entity;

import com.menthoros.enums.DiaSemanaEnum;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
@Getter
@Setter
public abstract class TreinoBase {

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false)
    protected DiaSemanaEnum diaSemana;

    @Column(name = "tipo_treino", nullable = false)
    protected String tipoTreino;

    @Column(name = "descricao", nullable = true, length = 1000)
    protected String descricao;

    @Column(name = "fc_alvo")
    protected String fcAlvo; // ex: "140-155"

    @Column(name = "duracao_min")
    protected Integer duracaoMin;

    @Column(name = "distancia_km")
    protected Double distanciaKm;

    @Column(name = "ritmo_alvo")
    protected String ritmoAlvo;

    @Column(name = "observacao", length = 1000)
    protected String observacao;

}
