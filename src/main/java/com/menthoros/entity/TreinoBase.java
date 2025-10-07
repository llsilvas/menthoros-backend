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

    /**
     * Elevação acumulada (gain) em metros
     * <p>Usado para ajustar TSS baseado em dificuldade do terreno:
     * <ul>
     *   <li>0m: Treino plano (fator = 1.0)</li>
     *   <li>300m em 20km (15m/km): Leve (fator ≈ 1.08)</li>
     *   <li>600m em 20km (30m/km): Moderado (fator ≈ 1.20)</li>
     *   <li>1200m em 20km (60m/km): Pesado (fator ≈ 1.50)</li>
     * </ul>
     * <p>Fonte: Minetti et al. (2002) - Energy cost of walking and running at extreme gradients
     */
    @Column(name = "elevacao_ganho_metros")
    protected Integer elevacaoGanhoMetros;

    /**
     * Elevação perdida (descida) em metros
     * <p>Importante para calcular fadiga muscular em descidas longas
     * (contração excêntrica causa maior DOMS - Delayed Onset Muscle Soreness)
     */
    @Column(name = "elevacao_perda_metros")
    protected Integer elevacaoPerdaMetros;

    @Column(name = "observacao", length = 1000)
    protected String observacao;

}
