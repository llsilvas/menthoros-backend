package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.Waitlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WaitlistRepository extends JpaRepository<Waitlist, UUID> {

    boolean existsByEmailNormalized(String emailNormalized);
}
