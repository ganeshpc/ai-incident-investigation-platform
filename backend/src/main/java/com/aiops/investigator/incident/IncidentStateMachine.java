package com.aiops.investigator.incident;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The allowed incident status changes.
 *
 * <p>A state machine is a fixed list of moves. Calling code asks this class before it writes a new
 * status. A move that is not listed throws, so a retry cannot silently jump a finished
 * investigation back to an unrelated state.
 */
public final class IncidentStateMachine {

  private static final Map<IncidentStatus, Set<IncidentStatus>> ALLOWED =
      new EnumMap<>(IncidentStatus.class);

  static {
    ALLOWED.put(IncidentStatus.CREATED, EnumSet.of(IncidentStatus.COLLECTING_EVIDENCE));
    ALLOWED.put(
        IncidentStatus.COLLECTING_EVIDENCE,
        EnumSet.of(IncidentStatus.EVIDENCE_READY, IncidentStatus.PARTIAL, IncidentStatus.FAILED));
    ALLOWED.put(
        IncidentStatus.EVIDENCE_READY,
        EnumSet.of(IncidentStatus.COLLECTING_EVIDENCE, IncidentStatus.INVESTIGATING));
    ALLOWED.put(
        IncidentStatus.INVESTIGATING,
        EnumSet.of(IncidentStatus.REPORT_READY, IncidentStatus.PARTIAL, IncidentStatus.FAILED));
    ALLOWED.put(
        IncidentStatus.PARTIAL,
        EnumSet.of(IncidentStatus.COLLECTING_EVIDENCE, IncidentStatus.INVESTIGATING));
    ALLOWED.put(
        IncidentStatus.FAILED,
        EnumSet.of(IncidentStatus.COLLECTING_EVIDENCE, IncidentStatus.INVESTIGATING));
    ALLOWED.put(
        IncidentStatus.REPORT_READY,
        EnumSet.of(IncidentStatus.COLLECTING_EVIDENCE, IncidentStatus.INVESTIGATING));
  }

  private IncidentStateMachine() {}

  /**
   * Rejects a status change that is not in the allowed list.
   *
   * @param from the status stored on the incident now
   * @param to the status the caller wants to store
   */
  public static void require(IncidentStatus from, IncidentStatus to) {
    Set<IncidentStatus> next = ALLOWED.get(from);
    if (next == null || !next.contains(to)) {
      throw new InvalidTransitionException(from, to);
    }
  }
}
