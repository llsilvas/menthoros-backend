package com.menthoros.entity;

import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.FonteDados;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tb_treino_realizado")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class TreinoRealizado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36, updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    @Column(name = "cadencia_media")
    private Integer cadenciaMedia;

    @Column(name = "data_treino", nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false)
    private DiaSemana diaSemana;

    @Column(name = "tipo_treino")
    private String tipoTreino;

    @Column(name = "duracao_min")
    private Integer duracaoMin;

    private Double distanciaKm;

    @Column(name = "fc_media")
    private Integer fcMedia;

    @Column(name = "fc_max")
    private Integer fcMax;

    @Column(name = "ritmo_medio")
    private String ritmoMedio;

    @Column(name = "potencia_media")
    private Integer potenciaMedia;

    @Column(name = "comentario")
    private String comentario;

    @Enumerated(EnumType.STRING)
    @Column(name = "fonte_dados")
    private FonteDados fonteDados;

    @ManyToOne(fetch = FetchType.LAZY)
    private PlanoTreino planoTreino;


}
