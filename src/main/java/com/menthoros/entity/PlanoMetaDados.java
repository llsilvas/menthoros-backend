package com.menthoros.entity;

import com.menthoros.converter.FloatListToVectorConverter;
import com.menthoros.enums.DiaSemana;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    @OneToOne(mappedBy = "planoMetaDados", fetch = FetchType.LAZY)
    private PlanoSemanal planoSemanalAtual;

    @Column(name = "embedding", columnDefinition = "vector(1536)")
    @Convert(converter = FloatListToVectorConverter.class)
    private List<Float> embedding;

    // ===== LIFECYCLE CALLBACKS =====

    @PreUpdate
    private void preUpdate() {
        this.dataUltimaAtualizacao = LocalDate.now();
        atualizarAlertas();
    }

    @PrePersist
    private void prePersist() {
        if (dataCriacao == null) {
            dataCriacao = LocalDateTime.now();
        }
        dataUltimaAtualizacao = LocalDate.now();
    }

    // ===== MÉTODOS DE NEGÓCIO =====

    private void atualizarAlertas() {
        // TSB muito baixo
        alertaSobrecarga = (tsbAtual != null && tsbAtual < -30);

        // Ramp rate muito alto
        alertaRampAlto = (rampRateAtual != null && rampRateAtual > 10);

        // Muitos dias seguidos
        alertaDiasConsecutivos = (diasConsecutivosTreino != null &&
                diasConsecutivosTreino >= 5);

        // Combinar alertas
        alertaNecessitaDescanso = alertaSobrecarga ||
                alertaDiasConsecutivos ||
                (tsbAtual != null && tsbAtual < -35);

        // Gerar mensagem
        mensagemAlerta = gerarMensagemAlerta();
    }

    private String gerarMensagemAlerta() {
        List<String> alertas = new ArrayList<>();

        if (alertaSobrecarga) {
            alertas.add("TSB crítico (" + tsbAtual + "). Descanso recomendado.");
        }

        if (alertaRampAlto) {
            alertas.add("Progressão muito rápida (" + rampRateAtual + " pts/sem). Reduzir volume.");
        }

        if (alertaDiasConsecutivos) {
            alertas.add(diasConsecutivosTreino + " dias seguidos treinando. Dia de descanso necessário.");
        }

        return alertas.isEmpty() ? null : String.join(" ", alertas);
    }

    @Transient
    public boolean estaEmFormaIdeal() {
        return tsbAtual != null && tsbAtual >= 5 && tsbAtual <= 10;
    }

    @Transient
    public boolean estaMuitoFatigado() {
        return tsbAtual != null && tsbAtual < -25;
    }

    @Transient
    public String getInterpretacaoTsb() {
        if (tsbAtual == null) return "Sem dados";
        if (tsbAtual < -30) return "Fadiga excessiva";
        if (tsbAtual < -20) return "Alta fadiga";
        if (tsbAtual < -10) return "Acumulando fadiga";
        if (tsbAtual < 0) return "Fatigado";
        if (tsbAtual < 5) return "Recuperando";
        if (tsbAtual < 15) return "Forma ideal";
        return "Muito descansado";
    }


}
