package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.MotivoPulo;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "tb_treino_planejado",
        indexes = {
                @Index(name = "idx_planejado_atleta_data", columnList = "atleta_id,data_treino"),
                @Index(name = "idx_planejado_status", columnList = "status_treino")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TreinoPlanejado extends TreinoBase{

    @Version
    @Column(name = "versao")
    private Long versao;

    @Column(name = "editado_pelo_coach", nullable = false)
    private boolean editadoPeloCoach = false;

    @Column(name = "adicionado_pelo_coach", nullable = false)
    private boolean adicionadoPeloCoach = false;

    @Column(name = "tss_planejado")
    private Integer tssPlanejado; // TSS estimado para o treino

    @Column(name = "intensidade_planejada")
    private Double intensidadePlanejada; // IF estimado (0.5 - 1.5)

    @Column(name = "percepcao_esforco_esperada")
    private Integer percepcaoEsforcoEsperada; // escala de 1 a 10

    @Column(name = "justificativa_ia", columnDefinition = "TEXT")
    private String justificativaIa; // Por que a IA sugeriu este treino

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plano_semanal_id")
    private PlanoSemanal planoSemanal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    // Vínculo com a prova quando tipoTreino = PROVA (prova-no-plano-semanal, D1). Nullable porque
    // nem todo treino é prova; ON DELETE SET NULL no banco cobre a deleção física da prova.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "prova_id")
    private Prova prova;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_treino", nullable = false)
    private TreinoExecucaoStatus statusTreino = TreinoExecucaoStatus.PENDENTE;

    // "Não vou conseguir hoje": o pulo é PERDIDO + motivo + carimbo, sem status novo (training-loop, D4).
    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_pulo", length = 20)
    private MotivoPulo motivoPulo;

    @Column(name = "pulado_em")
    private LocalDateTime puladoEm;

    // Marca da edição depois da aprovação (coach-editar-treino-aprovado, D3); nula = nunca editado.
    @Column(name = "edited_after_approval_at")
    private Instant editedAfterApprovalAt;

    @OneToMany(mappedBy = "treinoPlanejado", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("ordem ASC")
    private List<EtapaTreino> etapas;

    @OneToOne(mappedBy = "treinoPlanejado", fetch = FetchType.LAZY)
    private TreinoRealizado treinoRealizado;

    /**
     * Um realizado vinculou este planejado: o pulo deixa de valer. Chamar em todo caminho que leva
     * o status a REALIZADO — deixar o motivo para trás faria o coach ler um pulo num treino feito.
     */
    public void limparPulo() {
        this.motivoPulo = null;
        this.puladoEm = null;
    }

    // ===== INTEGRAÇÃO EXTERNA (PARA FUTURO) =====

    @Enumerated(EnumType.STRING)
    @Column(name = "fonte_dados", nullable = false)
    private FonteDados fonteDados = FonteDados.MANUAL;

    @Column(name = "external_id", length = 255)
    private String externalId; // ID do treino no Garmin/Strava/etc

    @Column(name = "url_externo", length = 500)
    private String urlExterno; // Link direto para o treino na plataforma

    @Enumerated(EnumType.STRING)
    @Column(name = "status_sincronizacao")
    private StatusSincronizacao statusSincronizacao = StatusSincronizacao.NAO_SINCRONIZADO;

    @Column(name = "sincronizado_em")
    private LocalDateTime sincronizadoEm;

    @Column(name = "ultima_tentativa_sincronizacao")
    private LocalDateTime ultimaTentativaSincronizacao;

    @Column(name = "tentativas_sincronizacao")
    private Integer tentativasSincronizacao = 0;

    @Column(name = "exportado_para", columnDefinition = "TEXT")
    private String exportadoPara; // JSON array: ["GARMIN", "STRAVA"]

    @Column(name = "erro_sincronizacao", columnDefinition = "TEXT")
    private String erroSincronizacao; // Detalhes do último erro

    @Column(name = "metadados_sincronizacao", columnDefinition = "TEXT")
    private String metadadosSincronizacao; // JSON com dados extras da sincronização



    // ===== LIFECYCLE CALLBACKS =====

    @PrePersist
    private void prePersist() {
        if (this.atleta == null && this.planoSemanal != null) {
            this.atleta = this.planoSemanal.getAtleta();
        }
        if (this.getTenantId() == null && this.planoSemanal != null && this.planoSemanal.getAssessoria() != null) {
            this.setTenantId(this.planoSemanal.getAssessoria().getId());
        }
        if (this.getTenantId() == null && this.atleta != null && this.atleta.getAssessoria() != null) {
            this.setTenantId(this.atleta.getAssessoria().getId());
        }
        if (this.statusTreino == null) {
            this.statusTreino = TreinoExecucaoStatus.PENDENTE;
        }
        if (this.fonteDados == null) {
            this.fonteDados = FonteDados.MANUAL;
        }
        if (this.statusSincronizacao == null) {
            this.statusSincronizacao = StatusSincronizacao.NAO_SINCRONIZADO;
        }
        if (this.getCriadoEm() == null) {
            this.setCriadoEm(LocalDateTime.now());
        }
        if (this.tentativasSincronizacao == null) {
            this.tentativasSincronizacao = 0;
        }
        // editadoPeloCoach já tem default false; garantir inicialização explícita
        this.setAtualizadoEm(LocalDateTime.now());
    }

    @PreUpdate
    private void preUpdate() {
        this.setAtualizadoEm(LocalDateTime.now());
    }

    // ===== MÉTODOS DE NEGÓCIO =====

    /**
     * Verifica se o treino foi realizado
     */
    public boolean foiRealizado() {
        return statusTreino == TreinoExecucaoStatus.CONCLUIDO &&
                treinoRealizado != null;
    }

    /**
     * Verifica se o treino foi criado por IA
     */
    public boolean foiGeradoPorIa() {
        return fonteDados == FonteDados.IA_GERADO ||
                (getCriadoPor() != null && getCriadoPor().equals("IA"));
    }

    /**
     * Verifica se o treino veio de plataforma externa
     */
    public boolean foiImportado() {
        return fonteDados != FonteDados.MANUAL &&
                fonteDados != FonteDados.IA_GERADO &&
                externalId != null;
    }

    /**
     * Verifica se precisa sincronizar
     */
    public boolean precisaSincronizar() {
        return statusSincronizacao == StatusSincronizacao.PENDENTE ||
                statusSincronizacao == StatusSincronizacao.AGUARDANDO_RETRY ||
                (statusSincronizacao == StatusSincronizacao.ERRO_TEMPORARIO &&
                        podeRetentarSincronizacao());
    }

    /**
     * Verifica se pode tentar sincronizar novamente
     */
    public boolean podeRetentarSincronizacao() {
        if (ultimaTentativaSincronizacao == null) return true;

        // Aguarda 5 minutos entre tentativas
        return ultimaTentativaSincronizacao
                .plusMinutes(5)
                .isBefore(LocalDateTime.now());
    }

    /**
     * Verifica se atingiu limite de tentativas de sincronização
     */
    public boolean atingiuLimiteTentativas() {
        return tentativasSincronizacao != null && tentativasSincronizacao >= 5;
    }

    /**
     * Registra tentativa de sincronização
     */
    public void registrarTentativaSincronizacao() {
        this.ultimaTentativaSincronizacao = LocalDateTime.now();
        this.tentativasSincronizacao = (tentativasSincronizacao == null ? 0 : tentativasSincronizacao) + 1;
    }

    /**
     * Marca como sincronizado com sucesso
     */
    public void marcarComoSincronizado(String plataforma) {
        this.statusSincronizacao = StatusSincronizacao.SINCRONIZADO;
        this.sincronizadoEm = LocalDateTime.now();
        this.erroSincronizacao = null;

        // Adiciona plataforma à lista de exportados
        if (this.exportadoPara == null || this.exportadoPara.isEmpty()) {
            this.exportadoPara = String.format("[\"%s\"]", plataforma);
        } else if (!this.exportadoPara.contains(plataforma)) {
            this.exportadoPara = this.exportadoPara.replace("]", String.format(",\"%s\"]", plataforma));
        }
    }

    /**
     * Marca erro de sincronização
     */
    public void marcarErroSincronizacao(StatusSincronizacao statusErro, String mensagemErro) {
        this.statusSincronizacao = statusErro;
        this.erroSincronizacao = mensagemErro;
        this.ultimaTentativaSincronizacao = LocalDateTime.now();
    }

    /** {@code SINCRONIZANDO} mais antigo que isto é preso: cada chamada HTTP ao canal tem timeout de segundos. */
    public static final Duration JANELA_SINCRONIZANDO = Duration.ofMinutes(10);

    /** {@code AGUARDANDO_RETRY} mais antigo que isto não está sendo pego: o scheduler roda a cada 15 min. */
    public static final Duration JANELA_AGUARDANDO_RETRY = Duration.ofMinutes(30);

    /**
     * Reinicia a sincronização de um treino a pedido do treinador: zera as tentativas e o erro e
     * volta a {@code AGUARDANDO_RETRY}, que o scheduler varre se o evento de push se perder. Usado
     * no reenvio de treino em {@code ERRO_*} e na edição de treino de plano aprovado, com ou sem
     * vínculo externo (coach-editar-treino-aprovado, D2). Grava {@code agora} como última tentativa para {@link #envioEmAndamento}
     * medir há quanto tempo o treino espera. Preserva {@code externalId} e {@code exportadoPara}
     * para o push seguinte atualizar o workout existente em vez de criar outro.
     */
    public void reiniciarSincronizacao(LocalDateTime agora) {
        this.statusSincronizacao = StatusSincronizacao.AGUARDANDO_RETRY;
        this.tentativasSincronizacao = 0;
        this.ultimaTentativaSincronizacao = agora;
        this.erroSincronizacao = null;
    }

    /**
     * Push em andamento: {@code AGUARDANDO_RETRY} ou {@code SINCRONIZANDO} com tentativa dentro da
     * janela de cada um. Fora da janela o treino é tratado como preso (atleta sem conexão, processo
     * que caiu no meio do claim) e não bloqueia o reenvio. {@code PENDENTE} fica de fora: é o treino
     * recém-aprovado que o listener ainda vai pegar, e o reenvio só reinicia {@code ERRO_*}.
     */
    public boolean envioEmAndamento(LocalDateTime agora) {
        Duration janela = switch (statusSincronizacao) {
            case AGUARDANDO_RETRY -> JANELA_AGUARDANDO_RETRY;
            case SINCRONIZANDO -> JANELA_SINCRONIZANDO;
            case null, default -> null;
        };
        return janela != null
                && ultimaTentativaSincronizacao != null
                && ultimaTentativaSincronizacao.isAfter(agora.minus(janela));
    }

    /**
     * Reseta a sincronização por completo: volta a {@code NAO_SINCRONIZADO} e limpa o vínculo
     * externo ({@code externalId}) — usado quando o evento externo deixa de existir (ex.: treino
     * sincronizado que virou não-exportável e foi reconciliado como órfão).
     */
    public void resetarSincronizacao() {
        this.statusSincronizacao = StatusSincronizacao.NAO_SINCRONIZADO;
        this.externalId = null;
        this.sincronizadoEm = null;
        this.tentativasSincronizacao = 0;
        this.erroSincronizacao = null;
        this.ultimaTentativaSincronizacao = null;
    }

    /**
     * Verifica se está no passado
     */
    @Transient
    public boolean estaNoPassado() {
        return dataTreino != null && dataTreino.isBefore(LocalDate.now());
    }

    /**
     * Verifica se é para hoje
     */
    @Transient
    public boolean eParaHoje() {
        return dataTreino != null && dataTreino.isEqual(LocalDate.now());
    }

    /**
     * Verifica se está no futuro
     */
    @Transient
    public boolean estaNoFuturo() {
        return dataTreino != null && dataTreino.isAfter(LocalDate.now());
    }

}
