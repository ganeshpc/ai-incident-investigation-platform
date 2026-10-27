package com.aiops.investigator.incident;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Loads and stores {@link Incident} rows. */
public interface IncidentRepository extends JpaRepository<Incident, UUID> {

  Optional<Incident> findByIdempotencyKey(String idempotencyKey);

  Page<Incident> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
