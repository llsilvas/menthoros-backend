package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.SignupProvisioning;
import br.com.menthoros.backend.enums.SignupProvisioningStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SignupProvisioningRepository extends JpaRepository<SignupProvisioning, java.util.UUID> {

    Optional<SignupProvisioning> findByIdempotencyKey(String idempotencyKey);

    /** Sustenta o limite anti-abuso por e-mail; apoia-se no índice (email, created_at) da V75. */
    long countByEmailAndCreatedAtAfter(String email, OffsetDateTime desde);

    /** Teto diário global — a mesma janela, sem recorte por e-mail. */
    long countByCreatedAtAfter(OffsetDateTime desde);

    /** Varredura operacional: o que a compensação não conseguiu limpar. */
    List<SignupProvisioning> findByStatusOrderByCreatedAtAsc(SignupProvisioningStatus status);
}
