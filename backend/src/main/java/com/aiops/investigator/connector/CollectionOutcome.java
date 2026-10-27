package com.aiops.investigator.connector;

import java.util.List;

/**
 * The result of one connector invocation, before it is written to PostgreSQL.
 *
 * <p>{@code evidence} is empty when the connector found nothing or could not read. The list is
 * copied so a caller cannot add a row after the connector has finished.
 *
 * @param sourceSystem the connector's source name
 * @param status how the invocation finished
 * @param evidence observations to store
 * @param warning a short problem sentence, or null
 * @param errorCategory a stable label such as MISSING_FIXTURE, or null
 * @param retryAppropriate true when running the same collection again might succeed
 * @param diagnosticMessage a sentence that is safe to store and show
 */
public record CollectionOutcome(
    String sourceSystem,
    CollectionStatus status,
    List<CollectedEvidence> evidence,
    String warning,
    String errorCategory,
    boolean retryAppropriate,
    String diagnosticMessage) {

  public CollectionOutcome {
    evidence = List.copyOf(evidence);
  }

  /** The connector read its fixture records. */
  public static CollectionOutcome success(
      String sourceSystem, List<CollectedEvidence> evidence, String diagnosticMessage) {
    return new CollectionOutcome(
        sourceSystem, CollectionStatus.SUCCESS, evidence, null, null, false, diagnosticMessage);
  }

  /** Some records were read and one expected record is absent. */
  public static CollectionOutcome partial(
      String sourceSystem,
      List<CollectedEvidence> evidence,
      String warning,
      String errorCategory,
      String diagnosticMessage) {
    return new CollectionOutcome(
        sourceSystem,
        CollectionStatus.PARTIAL,
        evidence,
        warning,
        errorCategory,
        false,
        diagnosticMessage);
  }

  /** The workload does not name this source. No vendor API was called. */
  public static CollectionOutcome unconfigured(String sourceSystem, String diagnosticMessage) {
    return new CollectionOutcome(
        sourceSystem,
        CollectionStatus.UNCONFIGURED,
        List.of(),
        null,
        null,
        false,
        diagnosticMessage);
  }

  /** The mapping exists, but the fixture file could not be read. */
  public static CollectionOutcome unavailable(
      String sourceSystem, String errorCategory, String diagnosticMessage) {
    return new CollectionOutcome(
        sourceSystem,
        CollectionStatus.UNAVAILABLE,
        List.of(),
        diagnosticMessage,
        errorCategory,
        false,
        diagnosticMessage);
  }

  /** The connector failed. {@code retryAppropriate} says whether another attempt might work. */
  public static CollectionOutcome failed(
      String sourceSystem,
      String errorCategory,
      boolean retryAppropriate,
      String diagnosticMessage) {
    return new CollectionOutcome(
        sourceSystem,
        CollectionStatus.FAILED,
        List.of(),
        diagnosticMessage,
        errorCategory,
        retryAppropriate,
        diagnosticMessage);
  }
}
