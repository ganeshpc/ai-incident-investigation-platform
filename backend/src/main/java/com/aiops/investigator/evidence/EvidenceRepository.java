package com.aiops.investigator.evidence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Reads evidence rows for one incident. Phase 2 does not insert them. */
public interface EvidenceRepository extends JpaRepository<EvidenceRecord, UUID> {

  @Query(
      """
      select evidence
      from EvidenceRecord evidence
      where evidence.incidentId = :incidentId
      order by evidence.eventTime asc nulls last, evidence.retrievalTime asc
      """)
  Page<EvidenceRecord> findForIncident(UUID incidentId, Pageable pageable);
}
