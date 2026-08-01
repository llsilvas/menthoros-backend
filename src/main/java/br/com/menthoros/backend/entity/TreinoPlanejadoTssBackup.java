package br.com.menthoros.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Valor de {@code tssPlanejado} anterior à correção do BUG-CONF-001, por treino.
 *
 * <p>Existe para tornar o recálculo <b>reversível</b>, não reconstruível: sem o valor original
 * guardado, desfazer exigiria reaplicar a fórmula antiga — o que é recomputar, não reverter, e
 * deixa de valer no dia em que a fórmula antiga sumir do código.
 *
 * <p>Uma linha por treino ({@code uk_treino_planejado_tss_backup_treino}), o que torna o recálculo
 * seguro para reexecução: rodar duas vezes não empilha snapshots nem sobrescreve o valor original
 * com um já corrigido.
 */
@Entity
@Table(name = "tb_treino_planejado_tss_backup")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TreinoPlanejadoTssBackup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "treino_planejado_id", nullable = false, unique = true)
    private UUID treinoPlanejadoId;

    @Column(name = "tss_planejado_antes", nullable = false)
    private Integer tssPlanejadoAntes;

    @Column(name = "migrado_em", nullable = false)
    private Instant migradoEm;

    @PrePersist
    void prePersist() {
        if (migradoEm == null) {
            migradoEm = Instant.now();
        }
    }
}
