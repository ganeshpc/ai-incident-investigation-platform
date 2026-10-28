package com.aiops.investigator.connector;

/**
 * One evidence source.
 *
 * <p>An interface is a list of methods a class promises to implement. The worker asks every
 * connector for this incident, then stores what they return. A connector reads files only. It must
 * not open a database connection and must not receive a URL, a shell command, or an unrestricted
 * path from the caller.
 */
public interface EvidenceConnector {

  /**
   * The source name stored on collection results, such as {@code AUTOSYS}.
   *
   * <p>One attempt stores one result per name. Two connectors must not return the same name.
   */
  String sourceSystem();

  /**
   * Reads this source for one incident.
   *
   * @param request the incident, its workload mapping, and the matched scenario if there is one
   */
  CollectionOutcome collect(CollectionRequest request);
}
