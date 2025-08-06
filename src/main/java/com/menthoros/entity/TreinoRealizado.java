package com.menthoros.entity;

import com.menthoros.enums.FonteDados;
import com.menthoros.enums.StatusTreino;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tb_treino_realizado")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TreinoRealizado extends TreinoBase{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36, updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treino_planejado_id")
    private TreinoPlanejado treinoPlanejado;

    @Column(name = "data_treino")
    private LocalDate dataTreino; // Opcional, mas útil

    @Column(name = "fc_media")
    private Integer fcMedia;

    @Column(name = "fc_max")
    private Integer fcMax;

    @Column(name = "ritmo_medio")
    private String ritmoMedio;

    @Column(name = "potencia_media")
    private Integer potenciaMedia;

    @Column(name = "cadencia_media")
    private Integer cadenciaMedia;

    @Column(name = "comentario")
    private String comentario;

    @Enumerated(EnumType.STRING)
    @Column(name = "fonte_dados")
    private FonteDados fonteDados;

    @Enumerated(EnumType.STRING)
    private StatusTreino status;

    @Column(name = "percepcao_esforco")
    private Integer percepcaoEsforco; // Ex: escala de 1 a 10

    @ManyToOne(fetch = FetchType.LAZY)
    private PlanoSemanal planoSemanal;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "tempo_execucao_segundos")
    private Integer tempoExecucaoSegundos;

    @Column(name = "elevacao_total")
    private Integer elevacaoTotalMetros;

}
