package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.domain.audit.AuditableEntity;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.Sexo;
import br.com.menthoros.backend.enums.TipoPlanoAtleta;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_atleta",
indexes = {
        @Index(name = "idx_atleta_ativo", columnList = "ativo"),
        @Index(name = "idx_atleta_nivel_experiencia", columnList = "nivel_experiencia")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Atleta extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nome", nullable = false)
    private String nome;
    
    @Column(name = "sobrenome", nullable = true)
    private String sobrenome;

    @Column(name = "data_nascimento", nullable = true)
    private LocalDate dataNascimento;

    @Column(name = "email", nullable = true, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "sexo", nullable = true, length = 20)
    private Sexo sexo;

    @Column(name = "peso_kg", precision = 5, scale = 2)
    private BigDecimal pesoKg;

    @Column(name = "altura_cm", precision = 5, scale = 2)
    private BigDecimal alturaCm;

    @Column(nullable = false, length = 500)
    private String objetivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_experiencia", nullable = false)
    private NivelExperiencia nivelExperiencia;

    // ===== DADOS FISIOLÓGICOS (essenciais para TSS) =====

    // Frequência Cardíaca
    @Column(name = "fc_maxima")
    private Integer fcMaxima; // Calculada: 220 - idade, ou testada

    @Column(name = "fc_repouso")
    private Integer fcRepouso; // Medida pela manhã

    @Column(name = "fc_limiar")
    private Integer fcLimiar; // ~85-90% FC máx, ou testada

    @Column(name = "data_ultimo_teste_fc")
    private LocalDate dataUltimoTesteFc;

    // Pace/Velocidade
    @Column(name = "pace_limiar", precision = 5, scale = 2)
    private BigDecimal paceLimiar; // min/km no limiar (ex: 4.5 min/km)

    @Column(name = "velocidade_limiar", precision = 5, scale = 2)
    private BigDecimal velocidadeLimiar; // km/h no limiar (ex: 13.3 km/h)

    @Column(name = "data_ultimo_teste_pace")
    private LocalDate dataUltimoTestePace;

    // VO2max estimado
    @Column(name = "vo2max_estimado", precision = 5, scale = 2)
    private BigDecimal vo2maxEstimado;

    // ===== CONSTANTES DE TEMPO ADAPTATIVAS PARA TSB =====

    /**
     * Constante de tempo para CTL (Chronic Training Load)
     * <p>Define velocidade de adaptação ao treinamento crônico:</p>
     * <ul>
     *   <li>30 dias: Iniciante (adapta rápido)</li>
     *   <li>35 dias: Intermediário</li>
     *   <li>42 dias: Avançado (padrão clássico)</li>
     *   <li>50 dias: Elite (adapta lento, mais estável)</li>
     * </ul>
     * <p>Valor padrão calculado por {@code NivelExperiencia} se não definido</p>
     */
    @Column(name = "ctl_time_constant")
    private Integer ctlTimeConstant;

    /**
     * Constante de tempo para ATL (Acute Training Load)
     * <p>Define velocidade de recuperação da fadiga aguda:</p>
     * <ul>
     *   <li>5 dias: Iniciante (recupera rápido, mas sobrecarrega fácil)</li>
     *   <li>6 dias: Intermediário</li>
     *   <li>7 dias: Avançado (padrão clássico)</li>
     *   <li>8 dias: Elite (recupera mais lento, maior resiliência)</li>
     * </ul>
     * <p>Valor padrão calculado por {@code NivelExperiencia} se não definido</p>
     */
    @Column(name = "atl_time_constant")
    private Integer atlTimeConstant;

    @ElementCollection(fetch = FetchType.LAZY)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "tb_dias_disponiveis", joinColumns = @JoinColumn(name = "atleta_id"))
    @Column(name = "dia")
    private List<DiaSemana> diasDisponiveis;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_preferido_longo")
    private DiaSemana diaPreferidoLongo;

    @Column(name = "distancia_maxima_longo")
    private Integer distanciaMaximaLongo; // km no longão

    @Column(name = "volume_semanal_max")
    private Integer volumeSemanalMax; // km/semana máximo confortável

    @Column(name = "tem_lesao")
    private Boolean temLesao = false;

    @Column(name = "descricao_lesao", length = 1000)
    private String descricaoLesao;

    @Column(name = "data_ultima_lesao")
    private LocalDate dataUltimaLesao;

    @Column(name = "historico_lesoes", columnDefinition = "TEXT")
    private String historicoLesoes;

    // ===== TIMEZONE (para reconciliação e agendamento) =====

    @Builder.Default
    @Column(name = "timezone", length = 50)
    private String timezone = "America/Sao_Paulo";

    @Enumerated(EnumType.STRING)
    @Column(name = "ativo")
    private AtletaStatus ativo;

    // ===== DADOS DE COBRANÇA (plano do atleta com a assessoria — distinto de PlanoAssessoria/PlanoMetaDados) =====

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_plano_atleta", length = 20)
    private TipoPlanoAtleta tipoPlanoAtleta;

    @Column(name = "data_vencimento_plano")
    private LocalDate dataVencimentoPlano;

    // Métricas atuais (OneToOne)
    @OneToOne(mappedBy = "atleta", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private PlanoMetaDados planoMetaDados;

    // Histórico de métricas diárias
    @OneToMany(mappedBy = "atleta", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MetricasDiarias> metricasDiarias;

    // Treinos realizados
    @OneToMany(mappedBy = "atleta", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TreinoRealizado> treinosRealizados;

    // Treinos planejados
    @OneToMany(mappedBy = "atleta", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TreinoPlanejado> treinosPlanejados;

    // Planos semanais
    @OneToMany(mappedBy = "atleta", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlanoSemanal> planosSemanais;

    // Provas
    @OneToMany(mappedBy = "atleta", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Prova> provas;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Assessoria assessoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    public Integer getFcMaximaCalculada() {
        Integer idade = getIdade();
        if (idade == null) {
            // Se não tem idade, retorna FC máxima se definida, ou um valor padrão conservador
            return fcMaxima != null ? fcMaxima : 180;
        }
        return fcMaxima != null ? fcMaxima : 220 - idade;
    }

    public Integer getFcMaximaComFallback() {
        if (this.getFcMaxima() != null && this.getFcMaxima() > 0) {
            return this.getFcMaxima();
        }
        // Fallback: 220 - idade
        if (this.getDataNascimento() != null) {
            int idade = java.time.Period.between(this.getDataNascimento(), java.time.LocalDate.now()).getYears();
            return 220 - idade;
        }
        // Último fallback: valor fixo seguro (exemplo: 190)
        return 190;
    }

    public Integer getIdade(){
        if (dataNascimento == null) {
            return null;
        }
        return LocalDate.now().getYear() - dataNascimento.getYear();
    }

    public Integer getFcLimiarCalculada() {
        return fcLimiar != null ? fcLimiar : (int) (0.85 * getFcMaximaCalculada());
    }

    public boolean precisaAtualizarTestes(){
        if(dataUltimoTesteFc == null || dataUltimoTestePace == null){
            return true;
        }

        LocalDate tresMesesAtras = LocalDate.now().minusMonths(3);
        return dataUltimoTesteFc.isBefore(tresMesesAtras) || dataUltimoTestePace.isBefore(tresMesesAtras);
    }

}