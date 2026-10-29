package br.com.menthoros.backend.scheduler;

import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.GarminOAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * D3 de garmin-connection: renova o refresh token (rotativo, ~90 dias) das conexões que estão a
 * menos de 30 dias de perdê-lo. Sem este job, atleta sem plano por 90 dias perderia a conexão sem
 * aviso. Filtra só por {@code refresh_token_expira_em}, que muda apenas no refresh.
 */
@Slf4j
@Component
public class GarminRefreshTokenScheduler {

    static final Duration JANELA = Duration.ofDays(30);

    private final IntegracaoExternaRepository integracaoRepository;
    private final GarminOAuthService oauthService;
    private final Clock clock;

    public GarminRefreshTokenScheduler(IntegracaoExternaRepository integracaoRepository,
                                       GarminOAuthService oauthService, Clock clock) {
        this.integracaoRepository = integracaoRepository;
        this.oauthService = oauthService;
        this.clock = clock;
    }

    /**
     * Idempotent: NO — o refresh token é rotativo; rodar de novo renova de novo (a janela de 30 dias
     * deixa de pegar a linha logo após a primeira renovação).
     * Side Effects: chamadas HTTP ao Garmin + Database update por conexão.
     * Tenant-aware: NO — cross-tenant por natureza; a renovação não depende de tenant.
     */
    @Scheduled(cron = "0 30 3 * * *")
    public void renovarRefreshTokensProximosDoVencimento() {
        var limite = clock.instant().plus(JANELA);
        int renovadas = 0;
        int falhas = 0;
        for (IntegracaoExterna conexao : integracaoRepository
                .findActiveByPlataformaAndRefreshTokenExpiraEmBefore(FonteDados.GARMIN, limite)) {
            try {
                oauthService.renovar(conexao);
                renovadas++;
            } catch (RuntimeException e) {
                // Uma conexão ruim não pode parar as demais; a mensagem nunca carrega token.
                falhas++;
                log.warn("Renovação diária Garmin falhou: atletaId={}, motivo={}",
                        conexao.getAtleta().getId(), e.getClass().getSimpleName());
            }
        }
        log.info("Renovação diária de refresh tokens Garmin: renovadas={}, falhas={}", renovadas, falhas);
    }
}
