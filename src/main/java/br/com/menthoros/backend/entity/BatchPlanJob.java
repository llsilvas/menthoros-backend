package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.BatchJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Job de geração de planos de treino em lote.
 *
 * <p>Criado no disparo do lote (status {@code PENDENTE}) e processado de forma
 * assíncrona em virtual threads. Os contadores {@code gerados}/{@code erros}
 * são incrementados atomicamente por atleta (ver {@code BatchPlanJobRepository});
 * {@code resultado} (JSON) só é preenchido no estado terminal.
 */
@Entity
@Table(name = "tb_batch_plan_job")
@Getter
@Setter
@NoArgsConstructor
public class BatchPlanJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BatchJobStatus status = BatchJobStatus.PENDENTE;

    @Column(name = "total_atletas", nullable = false)
    private int totalAtletas;

    @Column(name = "gerados", nullable = false)
    private int gerados;

    @Column(name = "erros", nullable = false)
    private int erros;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm = Instant.now();

    @Column(name = "concluido_em")
    private Instant concluidoEm;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resultado", columnDefinition = "jsonb")
    private String resultado;
}
