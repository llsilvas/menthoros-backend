package com.menthoros.entity;

import com.menthoros.enums.DiaSemana;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.*;

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
    @Column(length = 36,
            updatable = false,
            nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private int idade;

    @Column(nullable = false, length = 255)
    private String objetivo;

    @Column(name = "tsb_atual", nullable = false)
    private Long tsbAtual;

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "tb_dias_disponiveis", joinColumns = @JoinColumn(name = "atleta_id"))
    @Column(name = "dia_semana")
    private Set<DiaSemana> diasDisponiveis;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_preferido", nullable = false)
    private DiaSemana diaPreferido;

    @OneToMany(mappedBy = "atleta", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PlanoTreino> planos;

    @OneToMany(mappedBy = "atleta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TreinoRealizado> treinosRealizados;

    @OneToMany(mappedBy = "atleta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Prova> provas;

    public Optional<Prova> getProximaProvaComObjetivo() {
        return provas.stream()
                .filter(p -> p.getData().isAfter(LocalDate.now()) && p.getObjetivo() != null && !p.getObjetivo().isBlank())
                .min(Comparator.comparing(Prova::getData));
    }

    public Optional<Prova> getProximaProva() {
        return provas.stream()
                .filter(p -> p.getData().isAfter(LocalDate.now()))
                .min(Comparator.comparing(Prova::getData));
    }

    public void addPlanoTreino(PlanoTreino plano) {
        if (planos == null) {
            planos = new java.util.ArrayList<>();
        }
        planos.add(plano);
        plano.setAtleta(this);
    }

    public void removePlanoTreino(PlanoTreino plano) {
        if (planos != null) {
            planos.remove(plano);
            plano.setAtleta(null);
        }
    }
}
