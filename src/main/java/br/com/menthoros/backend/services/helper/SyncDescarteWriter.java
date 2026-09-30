package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.FonteDados;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Descarte registrado de atividades do pull (fix-sync-cursor-data-loss, design D7). Sem ele, uma
 * atividade rejeitada de forma permanente dentro do overlap é rebuscada todo ciclo, consome o teto
 * por ciclo e trava o backlog atrás dela; e uma que falha sempre prende o cursor para sempre.
 *
 * <p>Upsert em SQL ({@code ON CONFLICT}) em vez de entidade: o incremento de tentativas precisa ser
 * atômico, e ler-incrementar-salvar perderia contagem sob concorrência. Escritas em
 * {@code REQUIRES_NEW} para sobreviver ao rollback da transação da atividade que falhou.</p>
 *
 * <p>Só o pull agendado usa este bean — o sync manual não conta tentativa (design D7).</p>
 */
@Component
@RequiredArgsConstructor
public class SyncDescarteWriter {

    static final int TENTATIVAS_PARA_DESCARTE = 3;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Idempotent: SIM — descartar de novo mantém o primeiro {@code descartada_em}.
     * Side Effects: Database upsert.
     * Tenant-aware: SIM — grava o tenant recebido.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarPermanente(UUID tenantId, UUID atletaId, FonteDados plataforma, String externalId) {
        jdbc.update("""
                INSERT INTO tb_sync_atividade_descartada
                       (tenant_id, atleta_id, plataforma, external_id, motivo, tentativas, descartada_em, atualizado_em)
                VALUES (:tenant, :atleta, :plataforma, :externalId, 'PERMANENTE', 1, NOW(), NOW())
                ON CONFLICT (atleta_id, plataforma, external_id) DO UPDATE
                   SET motivo = 'PERMANENTE',
                       descartada_em = COALESCE(tb_sync_atividade_descartada.descartada_em, NOW()),
                       atualizado_em = NOW()
                """, params(tenantId, atletaId, plataforma, externalId));
    }

    /**
     * Conta uma falha inesperada; na {@value #TENTATIVAS_PARA_DESCARTE}ª, descarta.
     *
     * @return {@code true} se esta chamada (ou uma anterior) deixou a atividade descartada
     * Idempotent: NÃO — cada chamada é uma tentativa; o chamador chama no máximo uma vez por ciclo.
     * Side Effects: Database upsert.
     * Tenant-aware: SIM.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registrarFalha(UUID tenantId, UUID atletaId, FonteDados plataforma, String externalId) {
        Boolean descartada = jdbc.queryForObject("""
                INSERT INTO tb_sync_atividade_descartada
                       (tenant_id, atleta_id, plataforma, external_id, motivo, tentativas, descartada_em, atualizado_em)
                VALUES (:tenant, :atleta, :plataforma, :externalId, 'FALHA_RECORRENTE', 1, NULL, NOW())
                ON CONFLICT (atleta_id, plataforma, external_id) DO UPDATE
                   SET tentativas = tb_sync_atividade_descartada.tentativas + 1,
                       descartada_em = CASE
                           WHEN tb_sync_atividade_descartada.descartada_em IS NOT NULL
                               THEN tb_sync_atividade_descartada.descartada_em
                           WHEN tb_sync_atividade_descartada.tentativas + 1 >= :limite THEN NOW()
                           ELSE NULL END,
                       atualizado_em = NOW()
                RETURNING descartada_em IS NOT NULL
                """, params(tenantId, atletaId, plataforma, externalId).addValue("limite", TENTATIVAS_PARA_DESCARTE),
                Boolean.class);
        return Boolean.TRUE.equals(descartada);
    }

    /**
     * Idempotent: SIM (leitura).
     * Side Effects: nenhum.
     * Tenant-aware: SIM — filtra pelo tenant.
     */
    @Transactional(readOnly = true)
    public Set<String> descartadas(UUID tenantId, UUID atletaId, FonteDados plataforma) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT external_id FROM tb_sync_atividade_descartada
                 WHERE tenant_id = :tenant AND atleta_id = :atleta AND plataforma = :plataforma
                   AND descartada_em IS NOT NULL
                """, params(tenantId, atletaId, plataforma, null), String.class));
    }

    private static MapSqlParameterSource params(UUID tenantId, UUID atletaId, FonteDados plataforma,
                                                String externalId) {
        return new MapSqlParameterSource()
                .addValue("tenant", tenantId)
                .addValue("atleta", atletaId)
                .addValue("plataforma", plataforma.name())
                .addValue("externalId", externalId);
    }
}
