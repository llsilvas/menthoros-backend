package br.com.menthoros.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "tb_etapa_treino")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtapaTreino {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36, updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treino_planejado_id", nullable = false)
    private TreinoPlanejado treinoPlanejado;

    @Column(name = "ordem", nullable = false)
    private Integer ordem;

    @Column(name = "tipo_etapa", nullable = false)
    private String tipoEtapa;

    @Column(name = "descricao_etapa", length = 500)
    private String descricaoEtapa;

    @Column(name = "duracao_min")
    private Integer duracaoMin;

    @Column(name = "distancia_km", precision = 10, scale = 3)
    private BigDecimal distanciaKm;

    @Column(name = "fc_alvo_etapa")
    private String fcAlvoEtapa;

    @Column(name = "repeticoes")
    private Integer repeticoes;

    @Column(name = "ritmo_alvo", length = 20)
    private String ritmoAlvo;

}
