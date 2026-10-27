package com.aiops.investigator.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Stores {@link AuditEvent} rows. */
public interface AuditRepository extends JpaRepository<AuditEvent, UUID> {

  boolean existsByAttemptIdAndOperation(UUID attemptId, String operation);
}
