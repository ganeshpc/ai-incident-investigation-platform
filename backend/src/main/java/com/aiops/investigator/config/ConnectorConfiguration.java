package com.aiops.investigator.config;

import com.aiops.investigator.connector.EvidenceConnector;
import com.aiops.investigator.connector.ModeSelectingConnector;
import com.aiops.investigator.connector.ReadOnlyHttp;
import com.aiops.investigator.connector.RealAutosysConnector;
import com.aiops.investigator.connector.RealGithubConnector;
import com.aiops.investigator.connector.RealOpenShiftConnector;
import com.aiops.investigator.connector.RealReleaseConnector;
import com.aiops.investigator.connector.SyntheticAutosysConnector;
import com.aiops.investigator.connector.SyntheticEvidenceReader;
import com.aiops.investigator.connector.SyntheticGithubConnector;
import com.aiops.investigator.connector.SyntheticOpenShiftConnector;
import com.aiops.investigator.connector.SyntheticReleaseConnector;
import com.aiops.investigator.connector.SyntheticRunbookConnector;
import com.aiops.investigator.connector.SyntheticWorkflowConnector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers one connector bean per source.
 *
 * <p>A {@code @Bean} method returns an object Spring can inject. The synthetic classes are not
 * beans by themselves, so the worker cannot run both the fixture reader and the remote reader for
 * the same source. Runbooks and workflow have no remote API and stay on fixtures.
 */
@Configuration
public class ConnectorConfiguration {

  /** Scheduler connector. Real mode uses AEWS only when the basic credentials are set. */
  @Bean
  EvidenceConnector autosysConnector(
      SyntheticEvidenceReader reader, InvestigatorProperties properties) {
    InvestigatorProperties.Source settings = properties.getConnectors().getAutosys();
    return new ModeSelectingConnector(
        "AUTOSYS",
        "autosys",
        new SyntheticAutosysConnector(reader),
        request ->
            new RealAutosysConnector(
                    ReadOnlyHttp.basic(
                        settings.getBaseUrl(), settings.getUsername(), settings.getPassword()))
                .collect(request),
        settings,
        true);
  }

  /** Pod and log connector. Real mode uses the Kubernetes API and a bearer token. */
  @Bean
  EvidenceConnector openshiftConnector(
      SyntheticEvidenceReader reader, InvestigatorProperties properties) {
    InvestigatorProperties.Source settings = properties.getConnectors().getOpenshift();
    return new ModeSelectingConnector(
        "OPENSHIFT",
        "openshift",
        new SyntheticOpenShiftConnector(reader),
        request ->
            new RealOpenShiftConnector(
                    ReadOnlyHttp.bearer(settings.getBaseUrl(), settings.getToken()))
                .collect(request),
        settings,
        true);
  }

  /** Git connector. Real mode uses the GitHub REST API and a bearer token. */
  @Bean
  EvidenceConnector githubConnector(
      SyntheticEvidenceReader reader, InvestigatorProperties properties) {
    InvestigatorProperties.Source settings = properties.getConnectors().getGithub();
    return new ModeSelectingConnector(
        "GITHUB",
        "github",
        new SyntheticGithubConnector(reader),
        request ->
            new RealGithubConnector(ReadOnlyHttp.bearer(settings.getBaseUrl(), settings.getToken()))
                .collect(request),
        settings,
        true);
  }

  /**
   * Release connector.
   *
   * <p>{@code productIdentified} is false. Real mode does not call an API and does not read the
   * fixture.
   */
  @Bean
  EvidenceConnector releaseConnector(
      SyntheticEvidenceReader reader, InvestigatorProperties properties) {
    return new ModeSelectingConnector(
        "RELEASE",
        "release",
        new SyntheticReleaseConnector(reader),
        request -> null,
        properties.getConnectors().getRelease(),
        false);
  }

  /** Runbook connector. The files stay in the repository, so this source has no real mode. */
  @Bean
  EvidenceConnector runbookConnector(SyntheticEvidenceReader reader) {
    return new ModeSelectingConnector(
        "RUNBOOK",
        "runbooks",
        new SyntheticRunbookConnector(reader),
        request -> RealReleaseConnector.unconfigured(),
        new InvestigatorProperties.Source(),
        true);
  }

  /** Workflow connector. There is no remote case API, so the mode stays simulated in practice. */
  @Bean
  EvidenceConnector workflowConnector(
      SyntheticEvidenceReader reader, InvestigatorProperties properties) {
    InvestigatorProperties.Source simulated = new InvestigatorProperties.Source();
    return new ModeSelectingConnector(
        "WORKFLOW",
        "workflow",
        new SyntheticWorkflowConnector(reader),
        request -> null,
        simulated,
        true);
  }
}
