package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.Waitlist;
import br.com.menthoros.backend.enums.PerfilWaitlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WaitlistRepository extends JpaRepository<Waitlist, UUID> {

    boolean existsByEmailNormalized(String emailNormalized);

    List<Waitlist> findAllByPerfilAndDocsNotifiedAtIsNull(PerfilWaitlist perfil);

    /**
     * Reivindicação atômica do aviso da central de ajuda: só a chamada concorrente que ganha a
     * corrida grava {@code agora} e recebe {@code rowsUpdated == 1}; as demais recebem 0 e pulam
     * sem enviar de novo. Mesmo padrão de {@link AthleteInviteRepository#claim}.
     *
     * <p><strong>Idempotent:</strong> NO — por design: o claim é o mecanismo de exclusão mútua.
     * <p><strong>Side Effects:</strong> Database update.
     * <p><strong>Tenant-aware:</strong> NO — {@code Waitlist} é entidade global, pré-signup.
     */
    @Transactional // o serviço que chama roda sem transação (chamada externa de e-mail); o claim é atômico sozinho
    @Modifying
    @Query("UPDATE Waitlist w SET w.docsNotifiedAt = :agora WHERE w.id = :id AND w.docsNotifiedAt IS NULL")
    int reivindicarAvisoDocs(@Param("id") UUID id, @Param("agora") Instant agora);

    /**
     * Libera a reivindicação quando o envio falha, deixando o inscrito elegível no próximo
     * disparo. Incondicional por id (sem comparar o instante de volta): enquanto
     * {@code docsNotifiedAt} está preenchido, nenhuma outra chamada consegue reivindicar a
     * mesma linha (ver {@link #reivindicarAvisoDocs}) — não há "reivindicação alheia" para
     * pisar entre o claim e a falha. Mesmo padrão de {@code AthleteInviteRepository#liberarClaim}.
     */
    @Transactional
    @Modifying
    @Query("UPDATE Waitlist w SET w.docsNotifiedAt = NULL WHERE w.id = :id")
    void liberarAvisoDocs(@Param("id") UUID id);
}
