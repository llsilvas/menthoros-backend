package com.menthoros.entity;

import com.menthoros.converter.FloatListToVectorConverter;
import com.menthoros.dto.AlertaMetricas;
import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.FasePeriodizacao;
import com.menthoros.enums.NivelAlerta;
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
        atualizarAlertas();
        atualizarStatusGeral();
        atualizarRecomendacao();
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

        // Combinar alertas (TSB < -35 já está coberto por alertaSobrecarga que é TSB < -30)
        alertaNecessitaDescanso = alertaSobrecarga || alertaDiasConsecutivos;

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

    /**
     * Atualiza o status geral do atleta baseado nas métricas
     * Chamado automaticamente no @PreUpdate
     */
    private void atualizarStatusGeral() {
        if (tsbAtual == null && ctlAtual == null) {
            this.statusGeral = "COLETANDO DADOS";
            return;
        }

        // Prioridade 1: Alertas críticos combinados
        if (Boolean.TRUE.equals(alertaSobrecarga) && Boolean.TRUE.equals(alertaRampAlto)) {
            this.statusGeral = "FADIGA CRÍTICA + PROGRESSÃO RÁPIDA";
        }
        // Prioridade 2: Fadiga crítica (TSB < -35)
        else if (tsbAtual != null && tsbAtual < -35) {
            this.statusGeral = "FADIGA CRÍTICA";
        }
        // Prioridade 3: Ramp rate alto
        else if (Boolean.TRUE.equals(alertaRampAlto)) {
            this.statusGeral = "PROGRESSÃO MUITO RÁPIDA";
        }
        // Prioridade 4: Sobrecarga (TSB < -30)
        else if (Boolean.TRUE.equals(alertaSobrecarga)) {
            this.statusGeral = "FADIGA ALTA";
        }
        // Prioridade 5: Dias consecutivos
        else if (diasConsecutivosTreino != null && diasConsecutivosTreino >= 6) {
            this.statusGeral = "MUITOS DIAS CONSECUTIVOS";
        }
        // Prioridade 6: Fadiga moderada (TSB -30 a -20)
        else if (tsbAtual != null && tsbAtual < -20) {
            this.statusGeral = "FADIGA MODERADA";
        }
        // Prioridade 7: Acumulando fadiga (TSB -20 a -10)
        else if (tsbAtual != null && tsbAtual < -10) {
            this.statusGeral = "ACUMULANDO FADIGA";
        }
        // Prioridade 8: Fatigado (TSB -10 a 0)
        else if (tsbAtual != null && tsbAtual < 0) {
            this.statusGeral = "FATIGADO";
        }
        // Prioridade 9: Recuperando (TSB 0 a 5)
        else if (tsbAtual != null && tsbAtual < 5) {
            this.statusGeral = "RECUPERANDO";
        }
        // Prioridade 10: Forma ideal (TSB 5 a 15)
        else if (tsbAtual != null && tsbAtual <= 15) {
            this.statusGeral = "FORMA IDEAL";
        }
        // Prioridade 11: Descansado (TSB 15 a 25)
        else if (tsbAtual != null && tsbAtual <= 25) {
            this.statusGeral = "DESCANSADO";
        }
        // Prioridade 12: Muito descansado (TSB > 25)
        else if (tsbAtual != null) {
            this.statusGeral = "MUITO DESCANSADO";
        }
        // Default: Normal
        else {
            this.statusGeral = "NORMAL";
        }
    }

    /**
     * Atualiza a recomendação de treino baseada nos alertas ativos
     * Chamado automaticamente no @PreUpdate
     */
    private void atualizarRecomendacao() {
        StringBuilder rec = new StringBuilder();

        // Recomendações críticas primeiro
        if (tsbAtual != null && tsbAtual < -35) {
            rec.append("🔴 CRÍTICO: Dia de descanso completo OBRIGATÓRIO ou apenas atividade regenerativa leve (30min). ");
        } else if (Boolean.TRUE.equals(alertaSobrecarga)) {
            rec.append("⚠️ Reduzir volume em 30-40%. Priorizar treinos regenerativos e descanso. ");
        }

        if (Boolean.TRUE.equals(alertaRampAlto)) {
            rec.append("⚠️ Reduzir volume em 20-30% nas próximas 2 semanas. Não aumentar carga. ");
        }

        if (diasConsecutivosTreino != null && diasConsecutivosTreino >= 6) {
            rec.append("⚠️ Incluir dia de descanso completo IMEDIATAMENTE. ");
        } else if (Boolean.TRUE.equals(alertaDiasConsecutivos)) {
            rec.append("Incluir dia de descanso ou treino regenerativo nos próximos 1-2 dias. ");
        }

        if (semanasProgressaoContinua != null && semanasProgressaoContinua >= 4) {
            rec.append("Considerar semana regenerativa (reduzir volume em 40-50%) para assimilação. ");
        }

        // Recomendações por faixa de TSB (quando não há alertas críticos)
        if (rec.length() == 0 && tsbAtual != null) {
            if (tsbAtual < -20) {
                rec.append("⚠️ Fadiga moderada acumulada. Reduzir intensidade e priorizar recuperação entre sessões. ");
            } else if (tsbAtual < -10) {
                rec.append("Fadiga em acumulação. Evitar sessões intensas consecutivas e reforçar recuperação. ");
            } else if (tsbAtual < 0) {
                rec.append("Fadiga normal de treinamento. Respeitar intervalos de recuperação entre sessões intensas. ");
            } else if (tsbAtual < 5) {
                rec.append("Em fase de recuperação. Bom momento para treinos de base e consolidação. ");
            } else if (tsbAtual <= 15) {
                rec.append("✅ Condições ideais para treinos intensos ou provas importantes. ");
            } else if (tsbAtual <= 25) {
                rec.append("Descansado. Considerar incluir sessão de qualidade para manter adaptações. ");
            } else {
                rec.append("Muito descansado. Risco de perda de adaptações. Retomar volume gradualmente. ");
            }
        } else if (rec.length() == 0) {
            rec.append("Continuar treinamento normalmente, respeitando os princípios de progressão. ");
        }

        this.recomendacaoTreino = rec.length() > 0 ? rec.toString().trim() : null;
    }

    @Transient
    public boolean estaEmFormaIdeal() {
        return tsbAtual != null && tsbAtual >= 5 && tsbAtual <= 15;
    }

    @Transient
    public boolean estaMuitoFatigado() {
        return tsbAtual != null && tsbAtual < -30;
    }

    @Transient
    public String getInterpretacaoTsb() {
        if (tsbAtual == null) return "Sem dados";
        if (tsbAtual < -30) return "Fadiga excessiva";
        if (tsbAtual < -20) return "Alta fadiga";
        if (tsbAtual < -10) return "Acumulando fadiga";
        if (tsbAtual < 0) return "Fatigado";
        if (tsbAtual < 5) return "Recuperando";
        if (tsbAtual <= 15) return "Forma ideal";
        if (tsbAtual <= 25) return "Descansado";
        return "Muito descansado";
    }

    /**
     * Retorna lista de alertas ativos baseados nas métricas atuais
     * Usado para estruturar alertas de forma consistente entre entidade e prompt
     */
    @Transient
    public List<AlertaMetricas> getAlertasAtivos() {
        List<AlertaMetricas> alertas = new ArrayList<>();

        // Alerta TSB Crítico (< -35)
        if (tsbAtual != null && tsbAtual < -35) {
            alertas.add(new AlertaMetricas(
                    NivelAlerta.CRITICO,
                    "TSB_CRITICO",
                    String.format("TSB crítico (%.1f). Risco de overtraining.", tsbAtual),
                    "Dia de descanso completo OBRIGATÓRIO ou apenas atividade regenerativa leve (30min caminhada)."
            ));
        }
        // Alerta TSB Baixo (< -30)
        else if (alertaSobrecarga != null && alertaSobrecarga) {
            alertas.add(new AlertaMetricas(
                    NivelAlerta.ALTO,
                    "TSB_BAIXO",
                    String.format("TSB baixo (%.1f). Fadiga alta acumulada.", tsbAtual),
                    "Reduzir volume em 30-40%. Priorizar treinos regenerativos e descanso."
            ));
        }
        // Alerta TSB Moderado (< -20)
        else if (tsbAtual != null && tsbAtual < -20) {
            alertas.add(new AlertaMetricas(
                    NivelAlerta.ATENCAO,
                    "TSB_MODERADO",
                    String.format("TSB moderado (%.1f). Fadiga moderada.", tsbAtual),
                    "Monitorar sinais de fadiga. Considerar reduzir intensidade dos treinos."
            ));
        }

        // Alerta Ramp Rate Alto (> 10)
        if (alertaRampAlto != null && alertaRampAlto) {
            alertas.add(new AlertaMetricas(
                    NivelAlerta.CRITICO,
                    "RAMP_RATE_ALTO",
                    String.format("Progressão muito rápida (%.1f pts/sem). Risco de lesão!", rampRateAtual),
                    "Reduzir volume em 20-30% nas próximas 2 semanas. Não aumentar carga."
            ));
        }
        // Ramp Rate Moderado (> 8)
        else if (rampRateAtual != null && rampRateAtual > 8) {
            alertas.add(new AlertaMetricas(
                    NivelAlerta.ALTO,
                    "RAMP_RATE_MODERADO",
                    String.format("Progressão rápida (%.1f pts/sem).", rampRateAtual),
                    "Manter volume atual sem aumentar. Monitorar sinais de fadiga."
            ));
        }

        // Alerta Dias Consecutivos (>= 6)
        if (diasConsecutivosTreino != null && diasConsecutivosTreino >= 6) {
            alertas.add(new AlertaMetricas(
                    NivelAlerta.CRITICO,
                    "DIAS_CONSECUTIVOS_CRITICO",
                    String.format("%d dias consecutivos treinando. Risco de overtraining!", diasConsecutivosTreino),
                    "Incluir dia de descanso completo IMEDIATAMENTE."
            ));
        }
        // Dias Consecutivos Moderado (>= 5)
        else if (alertaDiasConsecutivos != null && alertaDiasConsecutivos) {
            alertas.add(new AlertaMetricas(
                    NivelAlerta.ALTO,
                    "DIAS_CONSECUTIVOS_ALTO",
                    String.format("%d dias consecutivos treinando.", diasConsecutivosTreino),
                    "Incluir dia de descanso ou treino regenerativo nos próximos 1-2 dias."
            ));
        }

        // Alerta Semanas de Progressão Contínua (>= 4)
        if (semanasProgressaoContinua != null && semanasProgressaoContinua >= 4) {
            alertas.add(new AlertaMetricas(
                    NivelAlerta.ATENCAO,
                    "PROGRESSAO_CONTINUA",
                    String.format("%d semanas de progressão contínua.", semanasProgressaoContinua),
                    "Considerar semana regenerativa (reduzir volume em 40-50%) para assimilação."
            ));
        }

        // Alerta Positivo - Forma Ideal
        if (alertas.isEmpty() && estaEmFormaIdeal()) {
            alertas.add(new AlertaMetricas(
                    NivelAlerta.INFO,
                    "FORMA_IDEAL",
                    String.format("TSB em forma ideal (%.1f). Condições ótimas!", tsbAtual),
                    "Janela ideal para treinos intensos ou provas importantes."
            ));
        }

        return alertas;
    }

    public double getTreinosPorSemanaMedio() {
        if (treinosPorSemanaMedio == null || treinosPorSemanaMedio == 0) {
            return 0;
        }
        return treinosPorSemanaMedio;
    }
}
