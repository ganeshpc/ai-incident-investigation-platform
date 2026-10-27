package com.aiops.investigator.workload;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Workload}.
 *
 * <p>A repository is an interface. Spring creates the class that runs the SQL. The method name
 * {@code findByWorkloadId} becomes a query for that column.
 */
public interface WorkloadRepository extends JpaRepository<Workload, String> {}
