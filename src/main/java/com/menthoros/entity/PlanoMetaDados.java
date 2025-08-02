package com.menthoros.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    @Column(length = 36,
            updatable = false,
            nullable = false)
    private UUID id;

    @Column(name = "volume_semanal_anterior")
    private Double volumeSemanalAnterior;

    @Column(name = "tsb_inicial")
    private Integer tsbInicial;

    @Column(name = "dia_preferido_longo")
    private String diaPreferidoLongo;

    @Column(name = "fonte_dados")
    private String fonteDados;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @OneToOne(mappedBy = "planoMetaDados", fetch = FetchType.LAZY)
    private PlanoTreino planoTreino;
}
