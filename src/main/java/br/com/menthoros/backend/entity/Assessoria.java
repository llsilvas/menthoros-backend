package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.PlanoAssessoria;
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

    @Column(name = "keycloak_organization_id", length = 100)
    private String keycloakOrganizationId;

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
    // Estado de cobrança (datas, trial, expiração) migrou para a entidade Assinatura
    // (assessoria-billing-asaas, design.md Decisão 8 / ADR-0004/0005). Aqui fica só o
    // tier (entitlement); Assessoria.ativo passa a ser escrito só pela sincronização
    // a partir do status de Assinatura.
    @Enumerated(EnumType.STRING)
    @Column(name = "plano", nullable = false)
    private PlanoAssessoria plano; // BASIC, PRO, ENTERPRISE

    // ===== CONTROLE =====
    @Builder.Default
    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    /**
     * Concorrência otimista. Duas abas do mesmo coach editando a assessoria produzem
     * {@code 409} em vez de lost update — a segunda escrita com versão obsoleta falha.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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
     * Verifica se a assessoria está ativa. A validade de cobrança (expiração/trial) não vive mais
     * aqui — {@code ativo} é sincronizado a partir do status de {@code Assinatura}
     * (assessoria-billing-asaas, design.md Decisão 8).
     */
    public boolean isValida() {
        return ativo;
    }

    /**
     * Verifica se atingiu o limite de atletas
     */
    public boolean podeAdicionarAtleta(int atletasAtuais) {
        return maxAtletas == null || atletasAtuais < maxAtletas;
    }
}
