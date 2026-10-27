package com.aiops.investigator.config;

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

  public String getFixturesRoot() {
    return fixturesRoot;
  }

  public void setFixturesRoot(String fixturesRoot) {
    this.fixturesRoot = fixturesRoot;
  }

  public Worker getWorker() {
    return worker;
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
}
