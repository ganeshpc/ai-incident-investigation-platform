package com.aiops.investigator.connector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Reads one fixture file from the repository and refuses paths that leave it.
 *
 * <p>{@link Path#normalize} removes {@code ..} segments. The result still has to stay inside the
 * repository root. The {@code evaluation} directory is the answer key, so a fixture path that
 * points there is rejected even though that directory is inside the repository.
 */
public final class FixtureDocuments {

  /** Excerpts stored on an evidence row stay at or below this many characters. */
  public static final int EXCERPT_LIMIT = 2000;

  /** Returns the text, or the first {@link #EXCERPT_LIMIT} characters plus a marker. */
  public static String bounded(String text) {
    if (text == null || text.length() <= EXCERPT_LIMIT) {
      return text == null ? "" : text;
    }
    return text.substring(0, EXCERPT_LIMIT) + "\n...[truncated]";
  }

  /** SHA-256 of a UTF-8 string, as 64 hexadecimal characters. */
  public static String sha256(String text) {
    return sha256(text.getBytes(StandardCharsets.UTF_8));
  }

  private FixtureDocuments() {}

  /**
   * The bytes and the bounded text of one repo-relative file.
   *
   * @param relativePath a path such as {@code fixtures/autosys/scenario-001/run-recon-failed.yaml}
   */
  public static FixtureFile read(Path repositoryRoot, String relativePath) {
    Path root = repositoryRoot.toAbsolutePath().normalize();
    if (relativePath == null || relativePath.isBlank() || relativePath.startsWith("/")) {
      throw new FixtureReadException("UNSAFE_PATH", "Fixture path is blank or absolute.");
    }
    Path resolved = root.resolve(relativePath).normalize();
    if (!resolved.startsWith(root)) {
      throw new FixtureReadException("UNSAFE_PATH", "Fixture path leaves the repository.");
    }
    Path evaluation = root.resolve("evaluation").normalize();
    if (resolved.startsWith(evaluation)) {
      throw new FixtureReadException("UNSAFE_PATH", "Fixture path points at the answer key.");
    }
    if (!Files.isRegularFile(resolved)) {
      throw new FixtureReadException(
          "MISSING_FIXTURE", "Fixture file does not exist: " + relativePath);
    }
    try {
      byte[] bytes = Files.readAllBytes(resolved);
      String text = new String(bytes, StandardCharsets.UTF_8);
      return new FixtureFile(relativePath, text, sha256(bytes), bounded(text));
    } catch (IOException ex) {
      throw new FixtureReadException(
          "MISSING_FIXTURE", "Fixture file could not be read: " + relativePath);
    }
  }

  /** SHA-256 of the file bytes, as 64 hexadecimal characters. */
  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  /**
   * One fixture file after it has been read.
   *
   * @param relativePath the path that was requested
   * @param text the full file as UTF-8
   * @param sha256 the hash of the bytes
   * @param excerpt the bounded text stored on the evidence row
   */
  public record FixtureFile(String relativePath, String text, String sha256, String excerpt) {}
}
