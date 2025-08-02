package com.menthoros.entity;

import com.menthoros.enums.PlanoStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_plano_treino")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PlanoTreino {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36, updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String descricao;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_prova", nullable = false)
    private LocalDate dataProva;

    @Column(nullable = false)
    private String objetivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PlanoStatus status;

    @OneToMany(mappedBy = "planoTreino", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TreinoPlanejado> treinosPlanejados;

    @OneToMany(mappedBy = "planoTreino", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TreinoRealizado> treinosRealizados;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "contexto_id", referencedColumnName = "id")
    private PlanoMetaDados planoMetaDados;
}
