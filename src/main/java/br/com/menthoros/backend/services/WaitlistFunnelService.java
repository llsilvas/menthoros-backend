package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.WaitlistFunnelBucketOutputDto;

import java.time.Instant;
import java.util.List;

/** Funil de inscrições da waitlist agrupado por origem (UTM) — uso administrativo (staff). */
public interface WaitlistFunnelService {

    /**
     * @param desde início do período (inclusive), ou {@code null} para sem limite inferior
     * @param ate   fim do período (inclusive), ou {@code null} para sem limite superior
     */
    List<WaitlistFunnelBucketOutputDto> calcularFunil(Instant desde, Instant ate);
}
