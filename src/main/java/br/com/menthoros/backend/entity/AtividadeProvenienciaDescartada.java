package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.FonteDados;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Auditoria append-only do dedup entre fontes do {@code ActivityDedupService}
 * (design.md Decisao 2, athlete-onboarding-baseline, V60). Guarda os dados da
 * atividade descartada no merge — nunca apagados, so nao ficam no registro
 * ativo (que grava apenas a coluna simples {@code proveniencia}).
 */
@Entity
@Table(name = "tb_atividade_proveniencia_descartada")
@Getter
@Setter
@NoArgsConstructor
public class AtividadeProvenienciaDescartada {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atividade_id", nullable = false)
    private TreinoRealizado atividade;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "fonte_descartada", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private FonteDados fonteDescartada;

    @Column(name = "dados_descartados", nullable = false, columnDefinition = "jsonb")
    private String dadosDescartados;

    @Column(name = "motivo_descarte", length = 255)
    private String motivoDescarte;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @PrePersist
    private void prePersist() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }
}
