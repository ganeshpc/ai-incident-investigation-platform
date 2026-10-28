package com.aiops.investigator.connector;

/**
 * Turns an HTTP failure into a collection result that does not use fixture data.
 *
 * <p>Authentication and not-found are unavailable. A timeout or a server error can be retried.
 * Every sentence says fixture data was not used, so a failed real call cannot be mistaken for a
 * synthetic success.
 */
final class RemoteOutcomes {

  private RemoteOutcomes() {}

  static CollectionOutcome from(String source, RemoteCallException failure) {
    String message = failure.getMessage() + " Fixture data was not used.";
    int status = failure.status();
    if (status == 401 || status == 403) {
      return CollectionOutcome.unavailable(source, "AUTHENTICATION", message);
    }
    if (status == 404) {
      return CollectionOutcome.unavailable(source, "NOT_FOUND", message);
    }
    if (status == 429 || status == 0 || status >= 500) {
      String category = status == 0 ? "TRANSPORT" : "SERVER";
      return CollectionOutcome.failed(source, category, true, message);
    }
    return CollectionOutcome.failed(source, "HTTP_" + status, false, message);
  }
}
