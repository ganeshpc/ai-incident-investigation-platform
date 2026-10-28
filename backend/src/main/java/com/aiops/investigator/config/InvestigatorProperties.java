package com.aiops.investigator.config;

import com.aiops.investigator.connector.ConnectorMode;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under the {@code investigator} key in {@code application.yml}.
 *
 * <p>A properties object copies configuration values into fields. Code reads the fields instead of
 * looking up strings. Durations such as {@code 30s} become {@link Duration} values.
 */
@ConfigurationProperties(prefix = "investigator")
public class InvestigatorProperties {

  private String fixturesRoot = "../fixtures";
  private final Worker worker = new Worker();
  private final Connectors connectors = new Connectors();

  public String getFixturesRoot() {
    return fixturesRoot;
  }

  public void setFixturesRoot(String fixturesRoot) {
    this.fixturesRoot = fixturesRoot;
  }

  public Worker getWorker() {
    return worker;
  }

  public Connectors getConnectors() {
    return connectors;
  }

  /** How often the worker looks for attempts, and how long a claim is reserved. */
  public static class Worker {

    private boolean scheduleEnabled = true;
    private long pollDelayMs = 1000;
    private Duration lease = Duration.ofSeconds(30);
    private int batchSize = 2;

    public boolean isScheduleEnabled() {
      return scheduleEnabled;
    }

    public void setScheduleEnabled(boolean scheduleEnabled) {
      this.scheduleEnabled = scheduleEnabled;
    }

    public long getPollDelayMs() {
      return pollDelayMs;
    }

    public void setPollDelayMs(long pollDelayMs) {
      this.pollDelayMs = pollDelayMs;
    }

    public Duration getLease() {
      return lease;
    }

    public void setLease(Duration lease) {
      this.lease = lease;
    }

    public int getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(int batchSize) {
      this.batchSize = batchSize;
    }
  }

  /**
   * Per-source mode and connection settings.
   *
   * <p>Each source is {@code simulated} unless an operator sets {@code real}. Real mode never reads
   * fixture files.
   */
  public static class Connectors {

    private final Source autosys = new Source();
    private final Source openshift = new Source();
    private final Source github = new Source();
    private final Source release = new Source();

    public Source getAutosys() {
      return autosys;
    }

    public Source getOpenshift() {
      return openshift;
    }

    public Source getGithub() {
      return github;
    }

    public Source getRelease() {
      return release;
    }
  }

  /**
   * One source's mode and credentials.
   *
   * <p>Passwords and tokens are read from the environment. This object does not print them.
   */
  public static class Source {

    private ConnectorMode mode = ConnectorMode.SIMULATED;
    private String baseUrl = "";
    private String username = "";
    private String password = "";
    private String token = "";

    public ConnectorMode getMode() {
      return mode;
    }

    public void setMode(ConnectorMode mode) {
      this.mode = mode;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    public String getToken() {
      return token;
    }

    public void setToken(String token) {
      this.token = token;
    }
  }
}
