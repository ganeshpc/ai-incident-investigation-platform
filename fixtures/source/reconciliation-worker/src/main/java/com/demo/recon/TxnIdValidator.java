package com.demo.recon;

/** Checks the transaction id on one synthetic reconciliation record. */
public final class TxnIdValidator {
  private TxnIdValidator() {}

  /**
   * Rejects a blank transaction id.
   *
   * @param transactionId value from the input column, possibly blank
   * @param recordNumber 1-based data row, excluding the header
   */
  public static void requireTransactionId(String transactionId, int recordNumber) {
    if (transactionId == null || transactionId.isBlank()) {
      throw new IllegalArgumentException(
          "record " + recordNumber + " missing required field transactionId");
    }
  }
}
