package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.ErroCategoriaPull;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.ResultadoPull;
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

import java.time.Instant;
import java.util.UUID;

/**
 * Um ciclo de pull de atividades de um atleta (fix-sync-cursor-data-loss, design D5): o instrumento
 * da métrica "treinos que não chegam" e da medição de add-sync-health-signal. Só os schedulers de
 * pull gravam; webhook, push e sync manual não. Retenção de 90 dias.
 *
 * <p>{@code atletaId} solto (sem associação): o registro é escrito e expurgado, nunca navegado.</p>
 */
@Entity
@Table(name = "tb_sync_pull_log")
@Getter
@Setter
@NoArgsConstructor
public class SyncPullLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "atleta_id", nullable = false)
    private UUID atletaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plataforma", nullable = false, length = 50)
    private FonteDados plataforma;

    @Column(name = "executado_em", nullable = false)
    private Instant executadoEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", nullable = false, length = 20)
    private ResultadoPull resultado;

    @Enumerated(EnumType.STRING)
    @Column(name = "erro_categoria", length = 40)
    private ErroCategoriaPull erroCategoria;

    @Column(name = "insercoes", nullable = false)
    private int insercoes;

    @Column(name = "ignoradas", nullable = false)
    private int ignoradas;
}
