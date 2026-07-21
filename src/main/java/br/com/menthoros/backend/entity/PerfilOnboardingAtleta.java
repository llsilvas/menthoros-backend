package br.com.menthoros.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Perfil de onboarding do atleta (design.md Decisao 10, athlete-onboarding-baseline, V61).
 *
 * <p>Guarda SOMENTE o status do draft (CA8) e os 5 campos genuinamente novos do formulario —
 * os outros 7 campos "obrigatorios" (objetivo, nivelExperiencia, diasDisponiveis,
 * historicoLesoes/temLesao/descricaoLesao/dataUltimaLesao, volumeSemanalMax) ja existem em
 * {@link Atleta} e sao escritos la diretamente, sem duplicar (evita duas fontes de verdade).
 */
@Entity
@Table(name = "tb_perfil_onboarding_atleta")
@Getter
@Setter
@NoArgsConstructor
public class PerfilOnboardingAtleta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "RASCUNHO"; // RASCUNHO | COMPLETO

    @Column(name = "maior_treino_recente_km")
    private BigDecimal maiorTreinoRecenteKm;

    @Column(name = "duracao_disponivel_min")
    private Integer duracaoDisponivelMin;

    @Column(name = "restricoes")
    private String restricoes;

    @Column(name = "modalidade", length = 30)
    private String modalidade;

    @Column(name = "percepcao_condicionamento", length = 30)
    private String percepcaoCondicionamento;

    @Column(name = "preenchido_por_coach", nullable = false)
    private boolean preenchidoPorCoach = false;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public boolean isCompleto() {
        return "COMPLETO".equals(status);
    }

    @PrePersist
    private void prePersist() {
        Instant agora = Instant.now();
        if (criadoEm == null) {
            criadoEm = agora;
        }
        atualizadoEm = agora;
    }

    @PreUpdate
    private void preUpdate() {
        atualizadoEm = Instant.now();
    }
}
