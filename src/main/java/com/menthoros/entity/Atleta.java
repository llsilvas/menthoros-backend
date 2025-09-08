package com.menthoros.entity;

import com.menthoros.enums.AtletaStatus;
import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.NivelExperiencia;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
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

    @Column(name = "peso_kg", precision = 5, scale = 2)
    private BigDecimal pesoKg;

    @Column(name = "altura_cm", precision = 5, scale = 2)
    private BigDecimal alturaCm;

    @Column(nullable = false)
    private String objetivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_experiencia", nullable = false)
    private NivelExperiencia nivelExperiencia;

    @ElementCollection(fetch = FetchType.LAZY)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "tb_dias_disponiveis", joinColumns = @JoinColumn(name = "atleta_id"))
    @Column(name = "dia")
    private List<DiaSemana> diasDisponiveis;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_preferido_longo")
    private DiaSemana diaPreferidoLongo;

    @Column(name = "tem_lesao")
    private Boolean temLesao;

    @Column(name = "descricao_lesao")
    private String descricaoLesao;

    @Enumerated(EnumType.STRING)
    @Column(name = "ativo")
    private AtletaStatus ativo;

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
