package br.com.menthoros.backend.enums;

public enum PerfilWaitlist {
    TREINADOR,
    /**
     * Dono de assessoria — mesmo vocabulário de {@link UserRole#PROPRIETARIO}, não {@code OWNER}:
     * duas palavras para o mesmo conceito no domínio seria pior que reaproveitar a já existente
     * (expand-waitlist-access-contract). Tratado como "parecido com treinador" em toda parte que
     * hoje só olha {@link #TREINADOR} — ver {@code Waitlist.isTreinadorOuProprietario()}.
     */
    PROPRIETARIO,
    ATLETA;

    /**
     * Única fonte de verdade para "tratado como treinador" — {@link br.com.menthoros.backend.entity.Waitlist#isTreinadorOuProprietario()}
     * delega aqui, e qualquer chamador que só tenha o perfil solto (ex.: ainda no DTO de entrada,
     * antes de a entidade existir) usa este método estático em vez de repetir a comparação.
     */
    public static boolean isTreinadorOuProprietario(PerfilWaitlist perfil) {
        return perfil == TREINADOR || perfil == PROPRIETARIO;
    }
}
