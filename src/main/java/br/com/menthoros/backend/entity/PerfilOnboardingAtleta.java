package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Perfil de onboarding do atleta (V61 + V65, athlete-onboarding-baseline).
 *
 * <p>Staging do formulario de onboarding (retrofit 10.3, sessao de grilling 2026-07-21 —
 * substitui a Decisao 10 original, que escrevia os campos espelhados DIRETO em {@link Atleta} a
 * cada step; ver {@code docs/adr/0002-draft-onboarding-nao-escreve-direto-em-atleta.md}).
 * Durante {@code RASCUNHO}, TODOS os campos do formulario — os 5 genuinamente novos e os que
 * tambem existem em {@link Atleta} (objetivo, nivelExperiencia, diasDisponiveis,
 * volumeSemanalMax, temLesao, descricaoLesao, dataUltimaLesao, historicoLesoes) — vivem SOMENTE
 * aqui. A migracao para {@link Atleta} so acontece na conclusao do onboarding
 * ({@code OnboardingService.concluirOnboarding}), numa unica transacao e com checagem de
 * conflito por {@code Atleta.updatedAt}.
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

    // ===== Staging dos campos espelhados de Atleta (retrofit 10.3) =====

    @Column(name = "objetivo", length = 500)
    private String objetivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_experiencia", length = 30)
    private NivelExperiencia nivelExperiencia;

    @ElementCollection(fetch = FetchType.LAZY)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "tb_dias_disponiveis_onboarding", joinColumns = @JoinColumn(name = "perfil_onboarding_atleta_id"))
    @Column(name = "dia")
    private List<DiaSemana> diasDisponiveis;

    @Column(name = "volume_semanal_max")
    private Integer volumeSemanalMax;

    @Column(name = "tem_lesao")
    private Boolean temLesao;

    @Column(name = "descricao_lesao", length = 1000)
    private String descricaoLesao;

    @Column(name = "data_ultima_lesao")
    private LocalDate dataUltimaLesao;

    @Column(name = "historico_lesoes", columnDefinition = "TEXT")
    private String historicoLesoes;

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
