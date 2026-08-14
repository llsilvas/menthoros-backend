package br.com.menthoros.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bytes da logo da assessoria, numa tabela 1:1 separada de {@link Assessoria}.
 *
 * <p><b>Por que não é uma coluna em {@code tb_assessoria}:</b> a assessoria é carregada em caminhos
 * quentes, e um LOB na própria entidade viaja em qualquer {@code SELECT} que o Hibernate gere —
 * {@code @Basic(fetch = LAZY)} sobre LOB é frágil sem instrumentação de bytecode. Entidade separada
 * torna o carregamento acidental impossível por construção.
 *
 * <p>Uma linha por assessoria: a PK <i>é</i> a FK. Trocar a logo é {@code UPDATE}, não append.
 */
@Entity
@Table(name = "tb_assessoria_logo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssessoriaLogo {

    @Id
    @Column(name = "assessoria_id")
    private UUID assessoriaId;

    /**
     * Conteúdo binário validado por decode — nunca os bytes crus do upload sem verificação.
     *
     * <p><b>Sem {@code @Lob}, deliberadamente.</b> No Postgres o Hibernate mapeia {@code @Lob} para
     * {@code oid} (large object), e large objects não são removidos quando a linha que os referencia
     * é apagada: o {@code ON DELETE CASCADE} desta tabela deixaria bytes órfãos em
     * {@code pg_largeobject}, invisíveis para a aplicação e permanentes sem {@code vacuumlo}.
     * {@code byte[]} puro mapeia para {@code bytea}, que morre junto com a linha.
     */
    @Column(name = "content", nullable = false)
    private byte[] content;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Integer sizeBytes;

    /**
     * Hash do conteúdo. É do conteúdo, não do instante: permite responder {@code 304} sem reler os
     * bytes e sobrevive a um restore que mude timestamps.
     */
    @Column(name = "etag", nullable = false, length = 64)
    private String etag;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void marcarAtualizacao() {
        this.updatedAt = OffsetDateTime.now();
    }
}
