package com.aiops.investigator.connector;

/**
 * A fixture path could not be read safely.
 *
 * <p>This is a runtime exception, so a connector can let it leave {@code collect} without declaring
 * {@code throws}. The orchestrator turns it into a collection result instead of losing the other
 * sources.
 */
public final class FixtureReadException extends RuntimeException {

  private final String category;

  /**
   * @param category a stable label such as MISSING_FIXTURE or UNSAFE_PATH
   * @param message a sentence that names the path and does not include file contents
   */
  public FixtureReadException(String category, String message) {
    super(message);
    this.category = category;
  }

  public String category() {
    return category;
  }
}
