package com.aiops.investigator.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Answers a small status request so a client can see which process is running.
 *
 * <p>{@link RestController} tells Spring MVC that the return value of each method is the response
 * body, not the name of an HTML template. {@link RequestMapping} adds a path prefix to every method
 * in the class. The incident API will use the {@code /api} prefix. This status route is not that
 * API.
 */
@RestController
@RequestMapping("/api/platform")
public class PlatformStatusController {

  /**
   * Returns the fixed identity of this bootstrap process.
   *
   * <p>{@link GetMapping} registers the method for HTTP GET. GET is the method a client uses when
   * it only wants to read. The method has no parameters because the answer does not depend on the
   * caller.
   *
   * @return the synthetic-environment status document
   */
  @GetMapping("/status")
  public PlatformStatus status() {
    return new PlatformStatus("investigator-backend", "synthetic-test", "bootstrap");
  }
}
