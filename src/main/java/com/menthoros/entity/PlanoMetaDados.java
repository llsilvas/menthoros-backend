package com.menthoros.entity;

import com.menthoros.enums.DiaSemanaEnum;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;


@Entity
@Table(name = "tb_plano_metadados")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PlanoMetaDados {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "volume_semanal_anterior")
    private Double volumeSemanalAnterior;

    @Column(name = "tsb_inicial")
    private Integer tsbInicial;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_preferido_longo", nullable = false)
    private DiaSemanaEnum diaPreferidoLongo;

    @OneToOne(mappedBy = "planoMetaDados", fetch = FetchType.LAZY)
    private PlanoSemanal planoSemanal;

    @Column
    private double atl = 0.0;

    @Column
    private double ctl = 0.0;

    @Column
    private double tsb = 0.0;

//    @JdbcTypeCode(SqlTypes.VECTOR) // Hibernate 6.x
//    @Column(name = "embedding", columnDefinition = "vector(1536)", nullable = true)
//    private float[] embedding;

}
