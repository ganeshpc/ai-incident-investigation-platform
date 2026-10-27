package com.demo.invoice;

import java.math.BigDecimal;

/** Parses a synthetic invoice amount written with a decimal point. */
public final class AmountParser {
  private AmountParser() {}

  /**
   * Accepts a decimal amount such as {@code 10.00}.
   *
   * @param raw amount column from the invoice file
   * @return the parsed amount
   */
  public static BigDecimal parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("amount is required");
    }
    return new BigDecimal(raw);
  }
}
