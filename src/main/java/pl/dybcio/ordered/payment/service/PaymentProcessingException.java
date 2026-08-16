package pl.dybcio.ordered.payment.service;

public class PaymentProcessingException extends RuntimeException {

  private final Long orderId;

  public PaymentProcessingException(Long orderId, Throwable cause) {
    super("Payment processing failed for order " + orderId, cause);
    this.orderId = orderId;
  }

  public Long getOrderId() {
    return orderId;
  }
}
