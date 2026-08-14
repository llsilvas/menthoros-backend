package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.AssessoriaLogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssessoriaLogoRepository extends JpaRepository<AssessoriaLogo, UUID> {

    /**
     * Existência sem carregar os bytes — usado pelo GET da assessoria para derivar
     * {@code temLogo}/{@code logoUrl}. Trazer o conteúdo aqui anularia o motivo de a tabela ser
     * separada.
     */
    boolean existsByAssessoriaId(UUID assessoriaId);

    /**
     * Só o {@code ETag}, para responder {@code 304} sem ler o conteúdo.
     */
    @Query("SELECT l.etag FROM AssessoriaLogo l WHERE l.assessoriaId = :assessoriaId")
    Optional<String> findEtagByAssessoriaId(@Param("assessoriaId") UUID assessoriaId);
}
