package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.converter.FloatListToVectorConverter;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FaixaTsb;
import br.com.menthoros.backend.enums.FasePeriodizacao;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


@Entity
@Table(name = "tb_plano_metadados",
        indexes = {
            @Index(name = "idx_plano_atleta", columnList = "atleta_id", unique = true)
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PlanoMetaDados {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atleta_id", nullable = false, unique = true)
    private Atleta atleta;

    /**
     * Tenant (Assessoria) ao qual estes metadados pertencem.
     * Garante isolamento de dados entre tenants diferentes.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Assessoria assessoria;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_ultima_atualizacao")
    private LocalDate dataUltimaAtualizacao;

    @Column(name = "ctl_atual")
    private Double ctlAtual = 0.0;

    @Column(name = "atl_atual")
    private Double atlAtual = 0.0;

    @Column(name = "tsb_atual")
    private Double tsbAtual = 0.0;

    @Column(name = "ramp_rate_atual")
    private Double rampRateAtual = 0.0;

    @Column(name = "volume_semanal_medio", precision = 10, scale = 2)
    private BigDecimal volumeSemanalMedio;

    @Column(name = "volume_planejado", precision = 10, scale = 2)
    private BigDecimal volumePlanejado;

    @Column(name = "tss_semanal_medio")
    private Integer tssSemanalMedio;

    @Column(name = "treinos_por_semana_medio")
    private Double treinosPorSemanaMedio;

    // ===== SEQUÊNCIAS E PADRÕES =====

    @Column(name = "dias_consecutivos_treino")
    private Integer diasConsecutivosTreino = 0;

    @Column(name = "dias_desde_ultimo_descanso")
    private Integer diasDesdeUltimoDescanso = 0;

    @Column(name = "semanas_progressao_continua")
    private Integer semanasProgressaoContinua = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_preferido_longo", nullable = false)
    private DiaSemana diaPreferidoLongo;

    // ===== ALERTAS AUTOMÁTICOS =====

    @Column(name = "alerta_sobrecarga")
    private Boolean alertaSobrecarga = false;

    @Column(name = "alerta_ramp_alto")
    private Boolean alertaRampAlto = false;

    @Column(name = "alerta_dias_consecutivos")
    private Boolean alertaDiasConsecutivos = false;

    @Column(name = "alerta_necessita_descanso")
    private Boolean alertaNecessitaDescanso = false;

    @Column(name = "mensagem_alerta", columnDefinition = "TEXT")
    private String mensagemAlerta;

    @OneToMany(mappedBy = "planoMetaDados", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlanoSemanal> planosSemanais;

    @Column(name = "embedding", columnDefinition = "vector(1536)")
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.OTHER)
    @Convert(converter = FloatListToVectorConverter.class)
    private List<Float> embedding;

    // ===== NOVOS CAMPOS - FASE 2 =====

    @Column(name = "status_geral", length = 50)
    private String statusGeral;

    @Column(name = "recomendacao_treino", columnDefinition = "TEXT")
    private String recomendacaoTreino;

    @Enumerated(EnumType.STRING)
    @Column(name = "fase_periodizacao", length = 30)
    private FasePeriodizacao fasePeriodizacao;

    // ===== LIFECYCLE CALLBACKS =====

    @PreUpdate
    private void preUpdate() {
        this.dataUltimaAtualizacao = LocalDate.now();
    }

    @PrePersist
    private void prePersist() {
        if (dataCriacao == null) {
            dataCriacao = LocalDateTime.now();
        }
        dataUltimaAtualizacao = LocalDate.now();
    }

    // ===== MÉTODOS DE NEGÓCIO =====

    /**
     * Aplica o resultado da análise de métricas nos campos da entidade.
     * Deve ser chamado antes do save, após {@code MetricasAlertaService.analisarMetricas()}.
     */
    public void aplicarAnalise(br.com.menthoros.backend.dto.output.ResultadoAnalise analise) {
        this.statusGeral = analise.statusGeral();
        this.recomendacaoTreino = analise.recomendacaoTreino();
        this.mensagemAlerta = analise.mensagemAlerta();
        this.alertaSobrecarga = analise.alertaSobrecarga();
        this.alertaRampAlto = analise.alertaRampAlto();
        this.alertaDiasConsecutivos = analise.alertaDiasConsecutivos();
        this.alertaNecessitaDescanso = analise.alertaNecessitaDescanso();
    }

    @Transient
    public boolean estaEmFormaIdeal() {
        FaixaTsb faixa = FaixaTsb.classificar(tsbAtual);
        return faixa != null && faixa.isFormaIdeal();
    }

    @Transient
    public boolean estaMuitoFatigado() {
        FaixaTsb faixa = FaixaTsb.classificar(tsbAtual);
        return faixa != null && faixa.isFadigaCritica();
    }

    @Transient
    public String getInterpretacaoTsb() {
        FaixaTsb faixa = FaixaTsb.classificar(tsbAtual);
        return faixa != null ? faixa.getInterpretacao() : "Sem dados";
    }

    @Transient
    public String interpretarTsb() {
        FaixaTsb faixa = FaixaTsb.classificar(tsbAtual);
        return faixa != null ? faixa.getStatus() : "Sem dados suficientes";
    }

    @Transient
    public String getRecomendacaoTsb() {
        FaixaTsb faixa = FaixaTsb.classificar(tsbAtual);
        return faixa != null ? faixa.getRecomendacao() : "";
    }

    @Transient
    public String avaliarStatusGeral() {
        if (statusGeral != null && !statusGeral.isBlank()) {
            return statusGeral;
        }
        FaixaTsb faixa = FaixaTsb.classificar(tsbAtual);
        if (faixa == null) {
            return "COLETANDO DADOS - Aguardando histórico de treinos para análise";
        }
        return faixa.getStatus();
    }

    public double getTreinosPorSemanaMedio() {
        if (treinosPorSemanaMedio == null || treinosPorSemanaMedio == 0) {
            return 0;
        }
        return treinosPorSemanaMedio;
    }
}
