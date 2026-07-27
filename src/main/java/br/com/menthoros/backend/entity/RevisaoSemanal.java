package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.FocusSource;
import br.com.menthoros.backend.enums.NivelAderencia;
import br.com.menthoros.backend.enums.RecommendationType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Revisão semanal do atleta, 1:1 com {@link PlanoSemanal} (ver ADR-0006 e o termo "Revisão Semanal"
 * no CONTEXT.md). Congela, no encerramento da semana, o sinal ESTRUTURADO do que foi proposto ao
 * coach — {@code recommendationType}, {@code adherenceStatus}, {@code sufficientData}.
 *
 * <p>Janela, TSB e volumes vêm do {@link PlanoSemanal} associado (join); {@code weekOverWeekDelta}
 * é computado na leitura, não persistido.
 *
 * <p>A revisão é estritamente <b>a proposta</b>: o que o coach faz depois com ela é registrado no
 * {@link PlanoSemanal} que a consumiu ({@code consumedReviewOutcome}), não aqui — preservando o
 * congelamento do ADR-0006.
 */
@Entity
@Table(name = "tb_revisao_semanal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class RevisaoSemanal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plano_semanal_id", nullable = false, unique = true)
    private PlanoSemanal planoSemanal;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_type", nullable = false, length = 20)
    private RecommendationType recommendationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "adherence_status", nullable = false, length = 10)
    private NivelAderencia adherenceStatus;

    /** Aderência por contagem na janela do plano (0–100), como-exibida — congelada. */
    @Column(name = "completion_rate", precision = 5, scale = 2)
    private BigDecimal completionRate;

    /** {@code false} se &lt;2 treinos realizados ou sem ponto de PMC/TSB válido (inclui tsbFim nulo). */
    @Column(name = "sufficient_data", nullable = false)
    private boolean sufficientData;

    /**
     * Foco proposto ao coach para a semana seguinte. Nunca nulo depois da geração: com a flag do LLM
     * desligada (ou em falha/reprovação do checker) é o template determinístico derivado do
     * {@code recommendationType}. Nunca exposto ao atleta.
     */
    @Column(name = "next_week_focus", columnDefinition = "TEXT")
    private String nextWeekFocus;

    /** Como o {@code nextWeekFocus} foi produzido — permite segmentar o sinal de aprendizado. */
    @Enumerated(EnumType.STRING)
    @Column(name = "focus_source", length = 10)
    private FocusSource focusSource;

    @Column(name = "gerada_em", nullable = false)
    private Instant geradaEm;
}
