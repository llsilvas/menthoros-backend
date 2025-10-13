package com.menthoros.entity;

import com.menthoros.enums.PlanoAssessoria;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_assessoria",
        indexes = {
                @Index(name = "idx_assessoria_dominio", columnList = "dominio", unique = true),
                @Index(name = "idx_assessoria_ativo", columnList = "ativo"),
                @Index(name = "idx_assessoria_keycloak_group", columnList = "keycloak_group_id", unique = true)
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assessoria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nome", nullable = false, length = 200)
    private String nome;

    @Column(name = "dominio", unique = true, length = 100)
    private String dominio; // Ex: "corridasserra", "teamx"

    // ===== INTEGRAÇÃO KEYCLOAK =====
    @Column(name = "keycloak_group_id", unique = true, length = 100)
    private String keycloakGroupId; // ID do Group no Keycloak

    @Column(name = "keycloak_realm", length = 100)
    private String keycloakRealm = "menthoros-app"; // Nome do realm no Keycloak

    @Column(name = "razao_social", length = 200)
    private String razaoSocial;

    @Column(name = "cnpj", length = 18, unique = true)
    private String cnpj;

    @Column(name = "email_contato", length = 100)
    private String emailContato;

    @Column(name = "telefone", length = 20)
    private String telefone;

    // ===== ENDEREÇO =====
    @Column(name = "logradouro", length = 200)
    private String logradouro;

    @Column(name = "numero", length = 10)
    private String numero;

    @Column(name = "complemento", length = 100)
    private String complemento;

    @Column(name = "bairro", length = 100)
    private String bairro;

    @Column(name = "cidade", length = 100)
    private String cidade;

    @Column(name = "estado", length = 2)
    private String estado;

    @Column(name = "cep", length = 9)
    private String cep;

    // ===== CONFIGURAÇÕES =====
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "cor_primaria", length = 7)
    private String corPrimaria; // Ex: "#FF6B35"

    @Column(name = "cor_secundaria", length = 7)
    private String corSecundaria;

    @Column(name = "max_atletas")
    private Integer maxAtletas; // Limite de atletas (plano)

    @Column(name = "max_tecnicos")
    private Integer maxTecnicos; // Limite de técnicos

    // ===== PLANO E COBRANÇA =====
    @Enumerated(EnumType.STRING)
    @Column(name = "plano", nullable = false)
    private PlanoAssessoria plano; // BASIC, PRO, ENTERPRISE

    @Column(name = "data_assinatura")
    private LocalDateTime dataAssinatura;

    @Column(name = "data_expiracao")
    private LocalDateTime dataExpiracao;

    @Column(name = "trial", nullable = false)
    private Boolean trial = false;

    @Column(name = "data_fim_trial")
    private LocalDateTime dataFimTrial;

    // ===== CONTROLE =====
    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== FEATURES FLAGS =====
    @Column(name = "feature_ia_avancada")
    private Boolean featureIaAvancada = false;

    @Column(name = "feature_relatorios_customizados")
    private Boolean featureRelatoriosCustomizados = false;

    @Column(name = "feature_integracao_strava")
    private Boolean featureIntegracaoStrava = true;

    @Column(name = "feature_api_externa")
    private Boolean featureApiExterna = false;

    // ===== RELACIONAMENTOS =====
    @OneToMany(mappedBy = "assessoria", fetch = FetchType.LAZY)
    private List<Atleta> atletas;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Verifica se a assessoria está ativa e dentro do período de validade
     */
    public boolean isValida() {
        if (!ativo) return false;

        if (trial && dataFimTrial != null) {
            return LocalDateTime.now().isBefore(dataFimTrial);
        }

        return dataExpiracao == null || LocalDateTime.now().isBefore(dataExpiracao);
    }

    /**
     * Verifica se atingiu o limite de atletas
     */
    public boolean podeAdicionarAtleta(int atletasAtuais) {
        return maxAtletas == null || atletasAtuais < maxAtletas;
    }
}
