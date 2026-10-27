package com.aiops.investigator.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Checks that fixture reads stay inside the repository and away from the answer key.
 *
 * <p>{@code @TempDir} gives the test an empty directory and deletes it afterwards. No database is
 * required.
 */
class FixtureDocumentsTest {

  @TempDir Path root;

  @Test
  void readsAFileInsideTheRepository() throws Exception {
    Path file = root.resolve("fixtures/autosys/job.yaml");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "jobName: DEMO\n");
    FixtureDocuments.FixtureFile read = FixtureDocuments.read(root, "fixtures/autosys/job.yaml");
    assertEquals("jobName: DEMO\n", read.excerpt());
    assertEquals(64, read.sha256().length());
  }

  @Test
  void rejectsAPathThatLeavesTheRepository() {
    FixtureReadException ex =
        assertThrows(
            FixtureReadException.class, () -> FixtureDocuments.read(root, "../outside.yaml"));
    assertEquals("UNSAFE_PATH", ex.category());
  }

  @Test
  void rejectsTheAnswerKey() throws Exception {
    Path answer = root.resolve("evaluation/ground-truth/secret.yaml");
    Files.createDirectories(answer.getParent());
    Files.writeString(answer, "cause: hidden\n");
    FixtureReadException ex =
        assertThrows(
            FixtureReadException.class,
            () -> FixtureDocuments.read(root, "evaluation/ground-truth/secret.yaml"));
    assertEquals("UNSAFE_PATH", ex.category());
    assertTrue(ex.getMessage().contains("answer key"));
  }
}
