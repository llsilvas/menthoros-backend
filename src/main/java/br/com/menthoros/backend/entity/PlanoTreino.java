package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.PlanoStatus;
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

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "prova_id", nullable = false)
    private Prova prova;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String descricao;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(nullable = false)
    private String objetivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PlanoStatus status;

    @OneToMany(mappedBy = "planoTreino", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlanoSemanal> planoSemanalList;

    @OneToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "contexto_id", referencedColumnName = "id")
    private PlanoMetaDados planoMetaDados;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prova_alvo_id")
    private Prova provaAlvo; // opcional: referência à prova alvo cadastrada
}
