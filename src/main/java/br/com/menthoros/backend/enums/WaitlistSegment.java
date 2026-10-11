package br.com.menthoros.backend.enums;

/**
 * Segmento derivado de um inscrito na waitlist — nunca persistido, só calculado na resposta
 * (expand-waitlist-access-contract, design D3), mesma filosofia de
 * {@code UsuarioLgpdConsent}: não existe flag equivalente armazenada, é derivada.
 */
public enum WaitlistSegment {
    ATLETA,
    QUALIFIED,
    OTHER_BRAND;

    public static WaitlistSegment derivar(PerfilWaitlist perfil, WatchBrand watchBrand) {
        if (perfil == PerfilWaitlist.ATLETA) {
            return ATLETA;
        }
        return watchBrand == WatchBrand.GARMIN ? QUALIFIED : OTHER_BRAND;
    }
}
