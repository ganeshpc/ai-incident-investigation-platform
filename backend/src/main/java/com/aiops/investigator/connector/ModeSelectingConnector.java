package com.aiops.investigator.connector;

import com.aiops.investigator.config.InvestigatorProperties;

/**
 * Chooses the simulated connector or the real connector for one source.
 *
 * <p>Simulated mode reads fixtures. Real mode calls the documented API only when the connection
 * settings are present. A missing setting or a failed call does not read fixtures. The release
 * source has no verified product, so real mode stays unconfigured even if a URL is set.
 */
public final class ModeSelectingConnector implements EvidenceConnector {

  private final String sourceSystem;
  private final String workloadSection;
  private final EvidenceConnector simulated;
  private final RealCall realCall;
  private final InvestigatorProperties.Source settings;
  private final boolean productIdentified;

  /**
   * @param productIdentified false only for release management, whose product is unknown
   * @param realCall the documented API, used only after the settings are present
   */
  public ModeSelectingConnector(
      String sourceSystem,
      String workloadSection,
      EvidenceConnector simulated,
      RealCall realCall,
      InvestigatorProperties.Source settings,
      boolean productIdentified) {
    this.sourceSystem = sourceSystem;
    this.workloadSection = workloadSection;
    this.simulated = simulated;
    this.realCall = realCall;
    this.settings = settings;
    this.productIdentified = productIdentified;
  }

  @Override
  public String sourceSystem() {
    return sourceSystem;
  }

  @Override
  public CollectionOutcome collect(CollectionRequest request) {
    if (!request.hasSection(workloadSection)) {
      return simulated.collect(request);
    }
    if (settings.getMode() != ConnectorMode.REAL) {
      return simulated.collect(request);
    }
    if (!productIdentified) {
      return RealReleaseConnector.unconfigured();
    }
    if (!ready()) {
      return CollectionOutcome.unconfigured(
          sourceSystem,
          sourceSystem
              + " real mode is selected but the connection settings are missing. Fixture data was not used.");
    }
    return realCall.collect(request);
  }

  /**
   * Whether this source has the settings its API requires.
   *
   * <p>AutoSys needs a base URL and the HTTP Basic user and password. OpenShift and GitHub need a
   * base URL and a bearer token.
   */
  private boolean ready() {
    if (!EvidenceTexts.httpUrl(settings.getBaseUrl())) {
      return false;
    }
    if ("AUTOSYS".equals(sourceSystem)) {
      return notBlank(settings.getUsername()) && notBlank(settings.getPassword());
    }
    return notBlank(settings.getToken());
  }

  /** True when the value has a character other than space. */
  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  /** The real read for one source. */
  @FunctionalInterface
  public interface RealCall {

    /** Calls the documented API. */
    CollectionOutcome collect(CollectionRequest request);
  }
}
