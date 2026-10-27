package com.aiops.investigator.incident;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * A stable hash of an investigate request.
 *
 * <p>The same fields in the same order always produce the same hash. The idempotency check compares
 * this hash with the hash stored on the first request. The header value itself is not part of the
 * hash.
 */
public final class RequestFingerprint {

  private RequestFingerprint() {}

  /**
   * @return 64 hexadecimal characters
   */
  public static String hash(
      String title,
      String workloadId,
      String environment,
      Instant failureTime,
      int lookbackMinutes,
      String autosysJobName,
      String jobRunId,
      IncidentType incidentType) {
    String canonical =
        String.join(
            "\n",
            empty(title),
            empty(workloadId),
            empty(environment),
            failureTime.toString(),
            Integer.toString(lookbackMinutes),
            empty(autosysJobName),
            empty(jobRunId),
            incidentType.name());
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  private static String empty(String value) {
    return value == null ? "" : value;
  }
}
