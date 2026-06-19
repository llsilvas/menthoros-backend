package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
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

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "plano_metadados_id", nullable = false)
    private PlanoMetaDados planoMetaDados; // Preferências do atleta + snapshot da semana

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Assessoria assessoria;

    @Column(name = "semana_inicio", nullable = false)
    private LocalDate semanaInicio;

    @Column(name = "semana_fim", nullable = false)
    private LocalDate semanaFim;

    @Column(name = "volume_planejado_km", nullable = false, precision = 10, scale = 3)
    private BigDecimal volumePlanejadoKm;

    @Column(name = "volume_realizado_km", precision = 10, scale = 3)
    private BigDecimal volumeRealizadoKm; // pode começar como null e ser preenchido depois

    @Column(name = "volume_alvo_km", precision = 10, scale = 3)
    private BigDecimal volumeAlvoKm;

    @Column(name = "tsb_inicio", precision = 10, scale = 3)
    private BigDecimal tsbInicio;

    @Column(name = "tsb_fim", precision = 10, scale = 3)
    private BigDecimal tsbFim;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PlanoStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false)
    private PlanoReviewStatus reviewStatus;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

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
