package com.aiops.investigator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts the investigation backend.
 *
 * <p>{@code public static void main} is the method the Java Virtual Machine calls when the program
 * starts. {@link SpringApplication#run} builds the application context, which is the object that
 * holds the beans Spring creates, and starts the embedded HTTP server.
 *
 * <p>This class is the composition root. Later phases add incident, evidence, and connector beans.
 * Phase 0 only proves the process starts without a database or the AI service.
 */
@SpringBootApplication
public class InvestigatorApplication {

  /**
   * Enters the backend process.
   *
   * @param args command-line arguments Spring Boot reads, such as {@code --server.port=8081}
   */
  public static void main(String[] args) {
    SpringApplication.run(InvestigatorApplication.class, args);
  }
}
