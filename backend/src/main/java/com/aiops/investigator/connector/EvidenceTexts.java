package com.aiops.investigator.connector;

import java.time.Instant;
import java.util.Map;

/**
 * Small text helpers shared by the real connectors.
 *
 * <p>A path segment is rejected when it could change which resource is requested. The JSON writer
 * escapes quotes so an attribute value cannot break the stored document.
 */
final class EvidenceTexts {

  private EvidenceTexts() {}

  /** True when the value can be one URL path segment. */
  static boolean safeSegment(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    return !value.contains("/")
        && !value.contains("\\")
        && !value.contains("..")
        && !value.contains("?")
        && !value.contains("#")
        && !value.contains(" ");
  }

  /** Encodes one path segment without encoding the slashes that are already outside it. */
  static String segment(String value) {
    return org.springframework.web.util.UriUtils.encodePathSegment(
        value, java.nio.charset.StandardCharsets.UTF_8);
  }

  /**
   * Parses an instant from an API time.
   *
   * <p>GitHub uses a trailing {@code Z}. AutoSys examples use {@code +0000}, which Java does not
   * parse until a colon is inserted.
   */
  static Instant instant(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String text = value;
    if (text.endsWith("Z")) {
      return Instant.parse(text);
    }
    if (text.length() > 5
        && (text.charAt(text.length() - 5) == '+' || text.charAt(text.length() - 5) == '-')) {
      String zone = text.substring(text.length() - 5);
      if (zone.charAt(3) != ':') {
        text =
            text.substring(0, text.length() - 5) + zone.substring(0, 3) + ":" + zone.substring(3);
      }
    }
    return Instant.parse(text);
  }

  /** A JSON object whose values are strings. */
  static String json(Map<String, String> fields) {
    StringBuilder out = new StringBuilder("{");
    boolean first = true;
    for (var entry : fields.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      if (!first) {
        out.append(',');
      }
      first = false;
      out.append('"')
          .append(escape(entry.getKey()))
          .append("\":\"")
          .append(escape(entry.getValue()))
          .append('"');
    }
    return out.append('}').toString();
  }

  /** True when the address is an http or https URL. */
  static boolean httpUrl(String value) {
    return value != null && (value.startsWith("https://") || value.startsWith("http://"));
  }

  /** Replaces characters that would break a JSON string. */
  private static String escape(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
