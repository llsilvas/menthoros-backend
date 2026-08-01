package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.TreinoPlanejadoTssBackup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Snapshot do {@code tssPlanejado} anterior à correção do BUG-CONF-001. */
@Repository
public interface TreinoPlanejadoTssBackupRepository extends JpaRepository<TreinoPlanejadoTssBackup, UUID> {

    boolean existsByTreinoPlanejadoId(UUID treinoPlanejadoId);

    Optional<TreinoPlanejadoTssBackup> findByTreinoPlanejadoId(UUID treinoPlanejadoId);
}
