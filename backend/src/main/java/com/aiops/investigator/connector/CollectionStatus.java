package com.aiops.investigator.connector;

/**
 * How one connector invocation finished.
 *
 * <p>An enum is a fixed set of names. These five names are the collection statuses stored in
 * PostgreSQL. {@code UNCONFIGURED} means the workload has no mapping for that source. {@code
 * UNAVAILABLE} means the mapping exists but the fixture could not be read. They are different
 * outcomes. A missing mapping is not reported as a broken integration.
 */
public enum CollectionStatus {
  SUCCESS,
  PARTIAL,
  UNAVAILABLE,
  UNCONFIGURED,
  FAILED
}
