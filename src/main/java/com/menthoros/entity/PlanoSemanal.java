package com.menthoros.entity;

import com.menthoros.enums.PlanoStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_plano_semanal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PlanoSemanal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plano_treino_id")
    private PlanoTreino planoTreino; // Opcional: plano de longo prazo (ex: para prova alvo)

    @OneToOne(optional = false, fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "plano_metadados_id", nullable = false)
    private PlanoMetaDados planoMetaDados; // Preferências do atleta + snapshot da semana

    @Column(name = "semana_inicio", nullable = false)
    private LocalDate semanaInicio;

    @Column(name = "semana_fim", nullable = false)
    private LocalDate semanaFim;

    @Column(name = "volume_planejado_km", nullable = false)
    private double volumePlanejadoKm;

    @Column(name = "volume_realizado_km")
    private Double volumeRealizadoKm; // pode começar como null e ser preenchido depois

    @Column(name = "volume_alvo_km")
    private Double volumeAlvoKm;

    @Column(name = "tsb_inicio")
    private Double tsbInicio;

    @Column(name = "tsb_fim")
    private Double tsbFim;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PlanoStatus status;

    @Column(name = "objetivo_semana")
    private String objetivoSemanal;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @OneToMany(mappedBy = "planoSemanal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TreinoPlanejado> treinosPlanejados;

    @Version
    @Column(name = "versao")
    private Long versao;

}
