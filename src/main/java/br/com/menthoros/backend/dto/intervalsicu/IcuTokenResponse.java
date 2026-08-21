package br.com.menthoros.backend.dto.intervalsicu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resposta de {@code POST https://intervals.icu/api/oauth/token}.
 *
 * <p><b>Não existe {@code refresh_token} nem {@code expires_in} nesta resposta</b>, e isso é o
 * contrato do provedor, não um campo esquecido (D3). O token não expira por tempo: ele morre
 * quando o atleta revoga no intervals.icu ou quando o app é desconectado. A versão anterior da
 * spec assumiu o contrato do Strava por analogia e mandava popular {@code tokenExpiraEm} a partir
 * de um {@code expires_in} que não vem — o código teria persistido nulo ou estourado NPE,
 * dependendo do parser.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IcuTokenResponse(
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("access_token") String accessToken,
        String scope,
        IcuAthleteDto athlete
) {}
