package com.menthoros.entity;

import com.menthoros.enums.DiaSemana;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_atleta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Atleta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(nullable = false)
    private int idade;

    @Column(nullable = false, length = 255)
    private String objetivo;

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "tb_dias_disponiveis", joinColumns = @JoinColumn(name = "atleta_id"))
    @Column(name = "dia")
    private List<DiaSemana> diasDisponiveis;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_preferido_longo")
    private DiaSemana diaPreferidoLongo;

    // Histórico de treinos realizados
    @OneToMany(mappedBy = "atleta", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TreinoRealizado> treinosRealizados;

    // Histórico de treinos planejados
    @OneToMany(mappedBy = "atleta", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TreinoPlanejado> treinosPlanejados;

    // Planos semanais
    @OneToMany(mappedBy = "atleta", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlanoSemanal> planosSemanais;

    // Provas associadas ao atleta
    @OneToMany(mappedBy = "atleta", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Prova> provas;

    @OneToMany(mappedBy = "atleta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlanoMetaDados> planosMetaDados;

}
