package com.aiops.investigator.connector;

/**
 * The real release adapter.
 *
 * <p>No release-management product has been identified, so this class does not call an API. The
 * fields an investigation needs, once a product is verified, are the application and environment,
 * the release identifier, the deployment status, the deployment time, and the artifact or commit
 * revision when the product actually provides it.
 */
public final class RealReleaseConnector {

  private RealReleaseConnector() {}

  /** A result that refuses both a guessed API and the fixture file. */
  public static CollectionOutcome unconfigured() {
    return CollectionOutcome.unconfigured(
        "RELEASE",
        "Release-management product is not identified. No release API was called. Fixture data was not used.");
  }
}
