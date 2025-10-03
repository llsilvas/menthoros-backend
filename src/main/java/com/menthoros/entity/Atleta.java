package com.menthoros.entity;

import com.menthoros.enums.AtletaStatus;
import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.NivelExperiencia;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_atleta",
indexes = {
        @Index(name = "idx_atleta_ativo", columnList = "ativo"),
        @Index(name = "idx_atleta_nivel_experiencia", columnList = "nivel_experiencia")
})
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

    @Column(nullable = false, length = 500)
    private String objetivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_experiencia", nullable = false)
    private NivelExperiencia nivelExperiencia;

    // ===== DADOS FISIOLÓGICOS (essenciais para TSS) =====

    // Frequência Cardíaca
    @Column(name = "fc_maxima")
    private Integer fcMaxima; // Calculada: 220 - idade, ou testada

    @Column(name = "fc_repouso")
    private Integer fcRepouso; // Medida pela manhã

    @Column(name = "fc_limiar")
    private Integer fcLimiar; // ~85-90% FC máx, ou testada

    @Column(name = "data_ultimo_teste_fc")
    private LocalDate dataUltimoTesteFc;

    // Pace/Velocidade
    @Column(name = "pace_limiar", precision = 5, scale = 2)
    private BigDecimal paceLimiar; // min/km no limiar (ex: 4.5 min/km)

    @Column(name = "velocidade_limiar", precision = 5, scale = 2)
    private BigDecimal velocidadeLimiar; // km/h no limiar (ex: 13.3 km/h)

    @Column(name = "data_ultimo_teste_pace")
    private LocalDate dataUltimoTestePace;

    // VO2max estimado
    @Column(name = "vo2max_estimado", precision = 5, scale = 2)
    private BigDecimal vo2maxEstimado;

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
