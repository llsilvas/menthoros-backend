package com.menthoros.entity;

import com.menthoros.enums.StatusTreino;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tb_treino_planejado")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TreinoPlanejado extends TreinoBase{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36,
            updatable = false,
            nullable = false)
    private UUID id;

    @Column(name = "observacao")
    private String observacao;

    @Column(name = "data_treino")
    private LocalDate dataTreino;

    @Column(name = "percepcao_esforco_esperada")
    private Integer percepcaoEsforcoEsperada; // escala de 1 a 10

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plano_semanal_id")
    private PlanoSemanal planoSemanal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    @Column
    private StatusTreino statusTreino;

    @PrePersist
    private void prePersist(){
        if(this.atleta == null && this.planoSemanal != null){
            this.atleta = this.planoSemanal.getAtleta();
        }
    }
}
