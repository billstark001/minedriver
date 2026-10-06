package io.github.billstark001.minedriver.api;

/** A failure with a stable machine-readable category. */
public final class DriverException extends RuntimeException {
  private final String code;

  public DriverException(String code, String message) {
    super(message);
    this.code = code;
  }

  public DriverException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
