package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Atleta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AtletaHojeResolver")
class AtletaHojeResolverTest {

    /** 03:50Z do dia 27 — em Manaus (UTC−4) ainda são 23:50 do dia 26. */
    private static final Instant MADRUGADA_UTC = Instant.parse("2026-08-27T03:50:00Z");

    private AtletaHojeResolver resolverEm(Instant instante) {
        return new AtletaHojeResolver(Clock.fixed(instante, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("às 23:50 locais de um fuso a oeste do servidor, hoje ainda é o dia anterior")
    void fusoDoAtletaVenceOFusoDoServidor() {
        Atleta atleta = Atleta.builder().timezone("America/Manaus").build();

        assertThat(resolverEm(MADRUGADA_UTC).hojeDe(atleta)).isEqualTo(LocalDate.of(2026, 8, 26));
    }

    @Test
    @DisplayName("às 00:10 locais o dia já virou, mesmo que o servidor ainda esteja no anterior")
    void meiaNoiteLocalViraODia() {
        // 04:10Z = 00:10 em Manaus (UTC−4); o servidor em UTC−5 ainda estaria no dia 26
        AtletaHojeResolver resolver = new AtletaHojeResolver(
                Clock.fixed(Instant.parse("2026-08-27T04:10:00Z"), ZoneOffset.ofHours(-5)));
        Atleta atleta = Atleta.builder().timezone("America/Manaus").build();

        assertThat(resolver.hojeDe(atleta)).isEqualTo(LocalDate.of(2026, 8, 27));
    }

    @Test
    @DisplayName("fuso inválido cai em America/Sao_Paulo sem quebrar")
    void fusoInvalidoCaiNoPadrao() {
        Atleta atleta = Atleta.builder().timezone("Marte/Olympus").build();

        // 03:50Z = 00:50 em São Paulo (UTC−3): já é dia 27
        assertThat(resolverEm(MADRUGADA_UTC).hojeDe(atleta)).isEqualTo(LocalDate.of(2026, 8, 27));
    }

    @Test
    @DisplayName("agoraDe devolve o instante no fuso do atleta")
    void agoraNoFusoDoAtleta() {
        Atleta atleta = Atleta.builder().timezone("America/Manaus").build();

        assertThat(resolverEm(MADRUGADA_UTC).agoraDe(atleta))
                .isEqualTo(java.time.LocalDateTime.of(2026, 8, 26, 23, 50));
    }

    @Test
    @DisplayName("fuso nulo cai em America/Sao_Paulo")
    void fusoNuloCaiNoPadrao() {
        Atleta atleta = Atleta.builder().timezone(null).build();

        assertThat(resolverEm(MADRUGADA_UTC).hojeDe(atleta)).isEqualTo(LocalDate.of(2026, 8, 27));
    }
}
