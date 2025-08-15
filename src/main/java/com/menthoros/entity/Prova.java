package com.menthoros.entity;

import com.menthoros.enums.StatusProva;
import com.menthoros.enums.TipoProva;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tb_prova")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Prova {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36, updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String nomeProva;

    @Column(nullable = false)
    private LocalDate dataProva;

    @Column(nullable = false)
    private Double distanciaKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_prova", nullable = false)
    private TipoProva tipoProva;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_prova", nullable = false)
    private StatusProva statusProva;

    @Column(name = "prova_alvo")
    private boolean provaAlvo;

    @Column(name = "objetivo")
    private String objetivo; // Ex: "Concluir abaixo de 1h50"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;
}
