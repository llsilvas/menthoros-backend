package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.SyncPullLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SyncPullLogRepository extends JpaRepository<SyncPullLog, UUID> {
}
