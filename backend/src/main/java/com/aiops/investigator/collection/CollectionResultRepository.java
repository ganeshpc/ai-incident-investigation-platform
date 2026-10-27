package com.aiops.investigator.collection;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Stores and reads collection results.
 *
 * <p>Spring Data implements this interface. The method name is the query: all results for one
 * incident.
 */
public interface CollectionResultRepository extends JpaRepository<CollectionResult, UUID> {

  List<CollectionResult> findByIncidentId(UUID incidentId);
}
