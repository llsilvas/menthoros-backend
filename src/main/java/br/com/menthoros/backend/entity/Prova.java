package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.TipoProva;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "tb_prova",
        indexes = {
                @Index(name = "idx_prova_atleta_data", columnList = "atleta_id,data_prova"),
                @Index(name = "idx_prova_tipo", columnList = "tipo_prova,data_prova")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Prova {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nomeProva;

    @Column(nullable = false)
    private LocalDate dataProva;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "distancia", nullable = false)
    private DistanciaProva distancia;

    @Column(name = "distancia_km", precision = 6, scale = 2)
    private BigDecimal distanciaKm; // Para distâncias customizadas

    @Column(name = "prova_alvo")
    private boolean provaAlvo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_prova", nullable = false)
    private TipoProva tipoProva;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_prova", nullable = false)
    private ProvaStatus statusProva = ProvaStatus.PLANEJADA;

    // ===== OBJETIVOS =====

    @Column(name = "tempo_objetivo")
    private LocalTime tempoObjetivo; // Meta de tempo (ex: 01:45:00)

    @Column(name = "pace_objetivo", precision = 5, scale = 2)
    private BigDecimal paceObjetivo; // min/km objetivo

    @Column(name = "tsb_ideal_prova")
    private Double tsbIdealProva; // TSB alvo no dia da prova (ex: +7)

    // ===== RESULTADO (se já realizada) =====

    @Column(name = "foi_realizada")
    private Boolean foiRealizada = false;

    @Column(name = "tempo_realizado")
    private LocalTime tempoRealizado;

    @Column(name = "posicao_geral")
    private Integer posicaoGeral;

    @Column(name = "posicao_categoria")
    private Integer posicaoCategoria;

    @Column(name = "tss_prova")
    private Integer tssProva; // TSS da prova (normalmente alto, 150-300)

    @Column(name = "percepcao_esforco_prova")
    private Integer percepcaoEsforcoProva;

    @Column(name = "feedback_prova", columnDefinition = "TEXT")
    private String feedbackProva;

    // ===== PERIODIZAÇÃO =====

    @Column(name = "semanas_preparacao")
    private Integer semanasPreparacao; // Quantas semanas de treino antes

    @Column(name = "inicio_preparacao")
    private LocalDate inicioPreparacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Assessoria assessoria;

    /**
     * Verifica se é a prova alvo principal
     */
    public boolean isProvaAlvo() {
        return provaAlvo;
    }

    /**
     * Calcula dias faltando para a prova
     */
    public int diasFaltando() {
        if (foiRealizada != null && foiRealizada) return 0;
        LocalDate hoje = LocalDate.now();
        if (dataProva.isBefore(hoje)) return 0;
        return (int) java.time.temporal.ChronoUnit.DAYS.between(hoje, dataProva);
    }

    /**
     * Verifica se está no período de taper (últimas 2 semanas)
     */
    public boolean estaNoPeriodoTaper() {
        int dias = diasFaltando();
        return dias <= 14 && dias > 0;
    }
}
