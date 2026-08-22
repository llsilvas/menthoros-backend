package br.com.menthoros.backend.config.external;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração do fluxo OAuth2 com o intervals.icu (app 663).
 *
 * <p><b>Por que todos os campos são {@code @NotBlank} (D11):</b> o {@code clientSecret} não é
 * apenas a credencial do token endpoint — ele é a <b>chave do HMAC que assina o {@code state}</b>
 * do callback público (D2). Com o secret vazio, a chave passa a ser {@code ""}, conhecida por
 * qualquer um, e o state assinado — que é a defesa central do fluxo — torna-se forjável para
 * qualquer {@code atletaId}. O modo de falha é silencioso: a aplicação sobe, conecta atletas e
 * passa em qualquer teste de caminho feliz. Só um atacante nota a diferença. Por isso a
 * configuração incompleta derruba o contexto no boot, que é o único momento barulhento disponível.
 *
 * <p><b>Por que {@code StravaProperties} não valida nada e está certo:</b> lá o {@code state} é um
 * UUID cru, não assinado. Um secret vazio no Strava quebra a troca de token com um 401 imediato e
 * visível. Mesma ausência de validação, consequências opostas — não copiar aquele arquivo aqui.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.intervals-icu")
public class IntervalsIcuProperties {

    @NotBlank
    private String baseUrl = "https://intervals.icu";

    @NotBlank
    private String clientId;

    /** Chave do HMAC do state, além de credencial do token endpoint. Ver JavaDoc da classe. */
    @NotBlank
    private String clientSecret;

    @NotBlank
    private String redirectUri;

    @NotBlank
    private String authorizationUri;

    /** Note o {@code /api} no caminho: {@code https://intervals.icu/api/oauth/token}. */
    @NotBlank
    private String tokenUri;

    /** Maiúsculo e separado por vírgula: {@code ACTIVITY:READ,CALENDAR:WRITE}. */
    @NotBlank
    private String scope;

    // --- scheduler de sync automático (intervals-icu-activity-sync-scheduler, D3/D4.1) ---

    /** Janela de fallback do primeiro ciclo, em dias. 90 (τ do CTL é 42; com 42 o CTL nasce 37% baixo). */
    @Min(1)
    private int syncDaysBack = 90;

    /**
     * Dias subtraídos do cursor ao calcular {@code oldest}. Nasceu como 1 (absorver a conversão
     * Instant → LocalDate); subiu para 7 no QA porque um relógio que fica uma semana sem sincronizar
     * entrega atividades com {@code start_date} anterior ao cursor — sem overlap elas nunca seriam
     * listadas. Custa zero requisições: o que já foi importado é filtrado antes de qualquer HTTP.
     */
    @Min(0)
    private int syncOverlapDays = 7;

    /**
     * Teto de atividades ainda não importadas que um ciclo busca por atleta. O provedor limita a
     * 100 req/usuário/dia e não expõe a folga em header; com 12 ciclos/dia, 1 + 6 = 7 por ciclo dá
     * 84. {@code @Min(1)} porque com 0 o lote fica vazio, o cursor não anda e o atleta relista a
     * mesma janela para sempre — sem erro nenhum.
     */
    @Min(1)
    private int syncMaxActivitiesPerCycle = 6;
}
