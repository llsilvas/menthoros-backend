package com.menthoros.entity;

import com.menthoros.enums.FonteDados;
import com.menthoros.enums.TreinoExecucaoStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tb_treino_realizado",
        indexes = {
                @Index(name = "idx_realizado_atleta_data", columnList = "atleta_id,data_treino"),
                @Index(name = "idx_realizado_data", columnList = "data_treino")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TreinoRealizado extends TreinoBase{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36, updatable = false, nullable = false)
    private UUID id;


    @Column(name = "data_treino")
    private LocalDate dataTreino; // Opcional, mas útil

    @Column(name = "tss_calculado")
    private Integer tssCalculado; // TSS real calculado

    @Column(name = "metodo_calculo_tss")
    private String metodoCalculoTss; // "FC", "PACE", "RPE"

    @Column(name = "fc_media")
    private Integer fcMedia;

    @Column(name = "fc_maxima_treino")
    private Integer fcMaximaTreino;

    @Column(name = "pace_media")
    private Double paceMedia; // min/km

    @Column(name = "velocidade_media")
    private Double velocidadeMedia; // km/h

    @Column(name = "percepcao_esforco")
    private Integer percepcaoEsforco; // RPE 1-10

    @Column(name = "intensidade_real")
    private Double intensidadeReal; // IF calculado

    // ===== FEEDBACK DO ATLETA =====

    @Column(name = "feedback_atleta", length = 1000)
    private String feedbackAtleta;

    @Column(name = "qualidade_sono_noite_anterior")
    private Integer qualidadeSonoNoiteAnterior; // 1-10

    @Column(name = "nivel_estresse")
    private Integer nivelEstresse; // 1-10

    @Enumerated(EnumType.STRING)
    @Column(name = "fonte_dados")
    private FonteDados fonteDados;

    @Enumerated(EnumType.STRING)
    private TreinoExecucaoStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treino_planejado_id")
    private TreinoPlanejado treinoPlanejado;

    @ManyToOne(fetch = FetchType.LAZY)
    private PlanoSemanal planoSemanal;

    @Column(name = "external_id")
    private String externalId;

    /**
     * Verifica se treino foi mais difícil que esperado
     */
    public boolean foiMaisDificilQueEsperado() {
        if (treinoPlanejado == null ||
                treinoPlanejado.getPercepcaoEsforcoEsperada() == null ||
                percepcaoEsforco == null) {
            return false;
        }

        return percepcaoEsforco > treinoPlanejado.getPercepcaoEsforcoEsperada() + 1;
    }

    /**
     * Calcula diferença entre TSS planejado vs realizado
     */
    public Integer getDiferencaTss() {
        if (treinoPlanejado == null ||
                treinoPlanejado.getTssPlaneado() == null ||
                tssCalculado == null) {
            return null;
        }

        return tssCalculado - treinoPlanejado.getTssPlaneado();
    }

}
