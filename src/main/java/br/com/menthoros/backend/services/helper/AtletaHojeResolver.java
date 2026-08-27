package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Atleta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Resolve o "hoje" de um atleta no fuso dele, não no do servidor.
 *
 * <p>O shell do atleta decide o estado do dia (treino de hoje, realizado de hoje, pulo) por esta
 * data. Às 23:50 em Manaus o servidor em UTC já virou o dia — usar {@code LocalDate.now(clock)}
 * mostraria o treino de amanhã e esconderia o registro de hoje.
 *
 * <p><b>Idempotente:</b> YES — leitura do relógio.
 * <p><b>Side Effects:</b> NONE.
 * <p><b>Tenant-aware:</b> NO — opera sobre o atleta que o chamador já resolveu.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AtletaHojeResolver {

    static final ZoneId FUSO_PADRAO = ZoneId.of("America/Sao_Paulo");

    private final Clock clock;

    public LocalDate hojeDe(Atleta atleta) {
        return LocalDate.now(clock.withZone(fusoDe(atleta)));
    }

    /** Agora no fuso do atleta — para carimbos que o próprio atleta vai ler ("pulei às 23:50"). */
    public LocalDateTime agoraDe(Atleta atleta) {
        return LocalDateTime.now(clock.withZone(fusoDe(atleta)));
    }

    private ZoneId fusoDe(Atleta atleta) {
        String timezone = atleta != null ? atleta.getTimezone() : null;
        if (timezone == null || timezone.isBlank()) {
            return FUSO_PADRAO;
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            // Fuso gravado inválido não pode derrubar a Home; o padrão é o do público do produto.
            log.warn("Timezone inválido no atleta {}: '{}' — usando {}", atleta.getId(), timezone, FUSO_PADRAO);
            return FUSO_PADRAO;
        }
    }
}
