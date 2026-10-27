package com.aiops.investigator.report;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reads the newest report for an incident. Phase 2 does not write reports. */
public interface ReportRepository extends JpaRepository<InvestigationReport, UUID> {

  Optional<InvestigationReport> findFirstByIncidentIdOrderByCreatedAtDesc(UUID incidentId);
}
