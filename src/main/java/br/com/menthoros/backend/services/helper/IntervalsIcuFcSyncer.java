package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.services.IntervalsIcuClient;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Reenvia {@code lthr}/{@code max_hr} do atleta ao intervals.icu. Sem isso o limiar do Menthoros e o da
 * conta do atleta lá divergem, e o relógio recebe FC alvo calculada com o número errado.
 *
 * <p><b>Idempotente:</b> YES — o PUT substitui o mesmo sport-settings a cada chamada.
 * <b>Side Effects:</b> External API call, best-effort — nunca lança. <b>Tenant-aware:</b> o tenant vem
 * por parâmetro (não usa {@code TenantContext}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntervalsIcuFcSyncer {

    private final IntervalsIcuClient intervalsIcuClient;
    private final IntervalsIcuConnectionService connectionService;

    /**
     * Sincroniza se o atleta tem intervals.icu conectado; sem conexão, não faz nada. Falha na leitura
     * da conexão ou na chamada é logada (sem o token) e engolida: quem chama já gravou os valores.
     */
    public void sincronizarSeConectado(Atleta atleta, UUID tenantId) {
        try {
            connectionService.conexaoAtiva(atleta.getId(), tenantId)
                    .filter(c -> StringUtils.hasText(c.getAccessToken()))
                    .ifPresent(c -> sincronizar(atleta, c));
        } catch (RuntimeException e) {
            log.warn("Reenvio de FC ao intervals.icu falhou (best-effort): {}", e.getClass().getSimpleName());
        }
    }

    private void sincronizar(Atleta atleta, IntegracaoExterna conexao) {
        sincronizar(atleta, conexao.getAccessToken(), conexao.getExternalAthleteId());
    }

    /**
     * Sem fcLimiar nem fcMaxima cadastrados, não manda nada — não sobrescreve o que o atleta já tem no
     * intervals.icu com null.
     */
    public void sincronizar(Atleta atleta, String token, String externalAthleteId) {
        Integer fcLimiar = atleta.getFcLimiar();
        Integer fcMaxima = atleta.getFcMaxima();
        if (fcLimiar == null && fcMaxima == null) {
            return;
        }
        ObjectNode payload = new ObjectMapper().createObjectNode();
        if (fcLimiar != null) {
            payload.put("lthr", fcLimiar);
        }
        if (fcMaxima != null) {
            payload.put("max_hr", fcMaxima);
        }
        try {
            intervalsIcuClient.atualizarSportSettings(token, externalAthleteId, "Run", payload);
        } catch (RuntimeException e) {
            log.warn("Sincronizacao de FC no intervals.icu falhou (best-effort, conexao segue): {}",
                    e.getClass().getSimpleName());
        }
    }
}
