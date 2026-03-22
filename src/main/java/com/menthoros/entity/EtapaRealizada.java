package com.menthoros.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

@Entity
@Table(name = "tb_etapa_realizada",
        indexes = {
                @Index(name = "idx_etapa_realizada_treino", columnList = "treino_realizado_id"),
                @Index(name = "idx_etapa_realizada_ordem", columnList = "treino_realizado_id,ordem")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtapaRealizada {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36, updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treino_realizado_id", nullable = false)
    private TreinoRealizado treinoRealizado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etapa_planejada_id")
    private EtapaTreino etapaPlanejada;

    @Column(name = "ordem", nullable = false)
    private Integer ordem;

    @Column(name = "tipo_etapa")
    private String tipoEtapa; // AQUECIMENTO, PRINCIPAL, INTERVALADO, RECUPERACAO, DESAQUECIMENTO

    @Column(name = "descricao", length = 500)
    private String descricao;

    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    @Column(name = "duracao")
    private Duration duracao;

    @Column(name = "distancia_km", precision = 10, scale = 3)
    private BigDecimal distanciaKm;

    @Column(name = "fc_media")
    private Integer fcMedia;

    @Column(name = "fc_max")
    private Integer fcMax;

    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    @Column(name = "pace_media")
    private Duration paceMedia;

    @Column(name = "velocidade_media", precision = 5, scale = 2)
    private BigDecimal velocidadeMedia; // km/h

    @Column(name = "percepcao_esforco")
    private Integer percepcaoEsforco; // RPE 1-10

    @Column(name = "cadencia_media")
    private Integer cadenciaMedia;

    @Column(name = "potencia_media")
    private Integer potenciaMedia;

    @Column(name = "observacao", length = 500)
    private String observacao;

}
