package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.PlannedWorkoutPostApprovalEdit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlannedWorkoutPostApprovalEditRepository extends JpaRepository<PlannedWorkoutPostApprovalEdit, UUID> {
}
