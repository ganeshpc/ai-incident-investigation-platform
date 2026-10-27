package com.aiops.investigator.attempt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Loads and stores {@link InvestigationAttempt} rows. */
public interface AttemptRepository extends JpaRepository<InvestigationAttempt, UUID> {

  List<InvestigationAttempt> findByIncidentIdOrderByAttemptNumberAsc(UUID incidentId);

  Optional<InvestigationAttempt> findFirstByIncidentIdOrderByAttemptNumberDesc(UUID incidentId);
}
