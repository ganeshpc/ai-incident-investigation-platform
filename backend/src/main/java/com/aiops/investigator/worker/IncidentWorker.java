package com.aiops.investigator.worker;

import com.aiops.investigator.config.InvestigatorProperties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls for stored attempts and advances each one outside the HTTP request.
 *
 * <p>The scheduled method wakes up after the previous run finishes. {@code schedule-enabled} can
 * turn that loop off in tests. Tests call {@link #runOnce()} directly so they do not wait for the
 * timer. The claim commits before {@link AttemptFinisher} runs, so a crash between the two leaves a
 * leased attempt that a later poll can pick up.
 */
@Component
public class IncidentWorker {

  private static final Logger log = LoggerFactory.getLogger(IncidentWorker.class);

  private final InvestigatorProperties properties;
  private final AttemptClaimer claimer;
  private final AttemptFinisher finisher;

  public IncidentWorker(
      InvestigatorProperties properties, AttemptClaimer claimer, AttemptFinisher finisher) {
    this.properties = properties;
    this.claimer = claimer;
    this.finisher = finisher;
  }

  /** Claims a batch of attempts and finishes each one. */
  public void runOnce() {
    for (UUID attemptId : claimer.claim()) {
      try {
        finisher.finish(attemptId);
      } catch (RuntimeException ex) {
        log.warn("Attempt {} failed: {}", attemptId, ex.getClass().getSimpleName());
        finisher.fail(attemptId, "The attempt failed before it could finish.");
      }
    }
  }

  /** Timer entry. It does nothing when the schedule is disabled. */
  @Scheduled(fixedDelayString = "${investigator.worker.poll-delay-ms:1000}")
  public void scheduled() {
    if (properties.getWorker().isScheduleEnabled()) {
      runOnce();
    }
  }
}
