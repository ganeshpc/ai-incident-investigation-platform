package com.aiops.investigator.connector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.json.JsonParseException;
import org.springframework.boot.json.JsonParserFactory;

/**
 * Reads the small set of JSON shapes the documented APIs return.
 *
 * <p>Spring's JSON parser turns a document into maps and lists. A field may be one object or a list
 * of objects. AutoSys examples use both shapes for {@code jobRun}, so callers ask for a list and
 * receive one element when the field is a single object.
 */
final class JsonRecords {

  private JsonRecords() {}

  /** Parses a JSON object. A document that is not an object raises a remote-call failure. */
  static Map<String, Object> object(String json, String source) {
    try {
      return JsonParserFactory.getJsonParser().parseMap(json);
    } catch (JsonParseException ex) {
      throw new RemoteCallException(0, source + " returned a document that is not JSON");
    }
  }

  /**
   * Returns the objects under one field.
   *
   * <p>A missing field is an empty list. A single object is wrapped. A list is returned as it is.
   */
  static List<Map<String, Object>> objects(Map<String, Object> document, String field) {
    Object value = document.get(field);
    if (value == null) {
      return List.of();
    }
    if (value instanceof Map<?, ?> map) {
      return List.of(stringKeys(map));
    }
    if (value instanceof List<?> list) {
      List<Map<String, Object>> objects = new ArrayList<>();
      for (Object item : list) {
        if (item instanceof Map<?, ?> map) {
          objects.add(stringKeys(map));
        }
      }
      return objects;
    }
    return List.of();
  }

  /**
   * Reads a scalar as text. Numbers are accepted because some examples quote them and some do not.
   */
  static String text(Object value) {
    if (value instanceof String text && !text.isBlank()) {
      return text;
    }
    if (value instanceof Number number) {
      return number.toString();
    }
    return null;
  }

  /** Copies a mapping and keeps only string keys. */
  private static Map<String, Object> stringKeys(Map<?, ?> raw) {
    java.util.LinkedHashMap<String, Object> copy = new java.util.LinkedHashMap<>();
    for (var entry : raw.entrySet()) {
      if (entry.getKey() instanceof String key) {
        copy.put(key, entry.getValue());
      }
    }
    return copy;
  }
}
