package com.menthoros.entity;

import com.menthoros.enums.DiaTreinoEnum;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "tb_treino_planejado")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class TreinoPlanejado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36,
            updatable = false,
            nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false)
    private DiaTreinoEnum diaSemana;

    @Column(name = "tipo_treino", nullable = false)
    private String tipoTreino;

    @Column(nullable = false)
    private String descricao;

    @Column(name = "fc_alvo")
    private String fcAlvo;

    @Column(name = "duracao_min", nullable = false)
    private Integer duracaoMin;

    @Column(nullable = false)
    private Double distancia;

    @Column(name = "ritmo_alvo")
    private String ritmoAlvo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plano_treino_id", nullable = false)
    private PlanoTreino planoTreino;
}
