package com.aiops.investigator.api;

/**
 * JSON document that identifies this process and the kind of data it serves.
 *
 * <p>A {@code record} is a Java class with fields fixed at construction. The compiler writes the
 * constructor, the accessors, {@code equals}, and {@code hashCode}. Spring MVC gives the record to
 * Jackson, the JSON library, which writes one JSON object for the HTTP response.
 *
 * @param service stable name of this process
 * @param environmentLabel label that tells a caller the deployment is synthetic
 * @param phase implementation phase this build represents
 */
public record PlatformStatus(String service, String environmentLabel, String phase) {}
