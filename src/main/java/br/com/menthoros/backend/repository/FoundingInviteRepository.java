package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.FoundingInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FoundingInviteRepository extends JpaRepository<FoundingInvite, UUID> {

    Optional<FoundingInvite> findByTokenHash(String tokenHash);

    /**
     * O convite "aberto" do inscrito — nem convertido nem invalidado — <strong>inclusive expirado
     * ou sem {@code sent_at}</strong>. É o que o reenvio precisa invalidar antes de inserir: o
     * índice parcial único não olha {@code expires_at}, então um expirado esquecido violaria a
     * UNIQUE. No máximo um por inscrito, garantido pelo índice.
     */
    @Query("""
            SELECT i FROM FoundingInvite i
            WHERE i.waitlistId = :waitlistId
              AND i.convertedAt IS NULL
              AND i.invalidatedAt IS NULL
            """)
    Optional<FoundingInvite> findOpenByWaitlistId(UUID waitlistId);

    boolean existsByWaitlistIdAndConvertedAtIsNotNull(UUID waitlistId);
}
