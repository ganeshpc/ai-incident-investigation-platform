package com.demo.invoice;

import java.math.BigDecimal;

/** Parses a synthetic invoice amount. */
public final class AmountParser {
  private AmountParser() {}

  /**
   * Rejects a decimal point. This behavior is the seeded regression.
   *
   * @param raw amount column from the invoice file
   * @return the parsed amount
   */
  public static BigDecimal parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("amount is required");
    }
    if (raw.indexOf('.') >= 0) {
      throw new IllegalArgumentException(
          "amount must be integer minor units; decimal point is not accepted");
    }
    return new BigDecimal(raw);
  }
}
