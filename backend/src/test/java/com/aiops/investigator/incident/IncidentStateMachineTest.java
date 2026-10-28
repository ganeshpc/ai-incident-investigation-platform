package com.aiops.investigator.incident;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Checks the status rules without a database.
 *
 * <p>These tests call the state machine directly. They do not start Spring or PostgreSQL.
 */
class IncidentStateMachineTest {

  @Test
  void createdCanBeginCollection() {
    assertDoesNotThrow(
        () ->
            IncidentStateMachine.require(
                IncidentStatus.CREATED, IncidentStatus.COLLECTING_EVIDENCE));
  }

  @Test
  void createdCannotJumpToReportReady() {
    assertThrows(
        InvalidTransitionException.class,
        () -> IncidentStateMachine.require(IncidentStatus.CREATED, IncidentStatus.REPORT_READY));
  }

  @Test
  void evidenceReadyCanBeCollectedAgain() {
    assertDoesNotThrow(
        () ->
            IncidentStateMachine.require(
                IncidentStatus.EVIDENCE_READY, IncidentStatus.COLLECTING_EVIDENCE));
  }

  @Test
  void finishedInvestigationCanBeReopenedOnlyThroughTheAllowedTargets() {
    assertDoesNotThrow(
        () ->
            IncidentStateMachine.require(
                IncidentStatus.REPORT_READY, IncidentStatus.INVESTIGATING));
    assertThrows(
        InvalidTransitionException.class,
        () -> IncidentStateMachine.require(IncidentStatus.REPORT_READY, IncidentStatus.CREATED));
  }
}
