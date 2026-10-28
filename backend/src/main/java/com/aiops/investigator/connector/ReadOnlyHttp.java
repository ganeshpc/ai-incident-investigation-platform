package com.aiops.investigator.connector;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Sends one GET request and returns the response body as text.
 *
 * <p>{@link RestClient} is Spring's HTTP client. This class sets a connect timeout and a read
 * timeout so a quiet server cannot hold the collection thread. It follows no write method. The
 * authorization value is sent and is not copied into exceptions.
 */
public final class ReadOnlyHttp {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

  private final RestClient client;
  private final String baseUrl;
  private final String authorization;

  private ReadOnlyHttp(String baseUrl, String authorization) {
    this.baseUrl = stripTrailingSlash(baseUrl);
    this.authorization = authorization;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT);
    factory.setReadTimeout(READ_TIMEOUT);
    this.client = RestClient.builder().requestFactory(factory).build();
  }

  /** A client that sends HTTP Basic credentials. AutoSys 12.1 documents this over HTTPS. */
  public static ReadOnlyHttp basic(String baseUrl, String username, String password) {
    String raw = username + ":" + password;
    String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    return new ReadOnlyHttp(baseUrl, "Basic " + encoded);
  }

  /** A client that sends a bearer token. Kubernetes and GitHub document this header. */
  public static ReadOnlyHttp bearer(String baseUrl, String token) {
    return new ReadOnlyHttp(baseUrl, "Bearer " + token);
  }

  /**
   * GETs one path and returns the body.
   *
   * <p>Each segment is encoded on its own, so a job name cannot add another path piece.
   *
   * @param segments path pieces after the base URL, such as {@code AEWS}, {@code job-run-info}
   * @param query query parameters, or an empty map
   * @param headers extra headers such as Accept. Authorization is added here.
   */
  public String get(List<String> segments, Map<String, String> query, Map<String, String> headers) {
    UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl);
    for (String segment : segments) {
      builder.pathSegment(segment);
    }
    query.forEach(builder::queryParam);
    URI uri = builder.build().toUri();
    try {
      return client
          .get()
          .uri(uri)
          .headers(http -> apply(http, headers))
          .retrieve()
          .body(String.class);
    } catch (RestClientResponseException ex) {
      throw new RemoteCallException(
          ex.getStatusCode().value(), "Remote read returned HTTP " + ex.getStatusCode().value());
    } catch (ResourceAccessException ex) {
      throw new RemoteCallException(0, "Remote read did not complete");
    }
  }

  /** Copies headers onto the request. The authorization value is not written to a log here. */
  private void apply(HttpHeaders target, Map<String, String> headers) {
    target.set(HttpHeaders.AUTHORIZATION, authorization);
    headers.forEach(target::set);
  }

  /** Turns a string map into Spring's query-parameter map. */
  private static org.springframework.util.MultiValueMap<String, String> toParams(
      Map<String, String> query) {
    org.springframework.util.LinkedMultiValueMap<String, String> params =
        new org.springframework.util.LinkedMultiValueMap<>();
    query.forEach(params::add);
    return params;
  }

  /** Removes one trailing slash so a path is not joined as a double slash. */
  private static String stripTrailingSlash(String baseUrl) {
    if (baseUrl.endsWith("/")) {
      return baseUrl.substring(0, baseUrl.length() - 1);
    }
    return baseUrl;
  }
}
