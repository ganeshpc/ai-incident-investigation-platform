package com.aiops.investigator.connector;

/**
 * Whether a source reads fixtures or a verified remote API.
 *
 * <p>An enum is a fixed set of names. {@code SIMULATED} is the default and reads fixture files.
 * {@code REAL} calls only the documented read API. It does not fall back to fixtures when the call
 * fails or the settings are missing.
 */
public enum ConnectorMode {
  SIMULATED,
  REAL
}
