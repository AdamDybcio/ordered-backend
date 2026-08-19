package pl.dybcio.ordered.common.exception;

import java.util.stream.Collectors;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.dybcio.ordered.address.service.AddressNotFoundException;
import pl.dybcio.ordered.cart.service.CartItemNotFoundException;
import pl.dybcio.ordered.cart.service.EmptyCartException;
import pl.dybcio.ordered.catalog.service.ProductNotActiveException;
import pl.dybcio.ordered.catalog.service.ProductNotFoundException;
import pl.dybcio.ordered.catalog.service.ProductOwnershipException;
import pl.dybcio.ordered.order.service.InsufficientStockException;
import pl.dybcio.ordered.order.service.InvalidOrderStatusTransitionException;
import pl.dybcio.ordered.order.service.OrderNotFoundException;
import pl.dybcio.ordered.order.service.OrderStatusChangeNotAllowedException;
import pl.dybcio.ordered.payment.service.PaymentProcessingException;
import pl.dybcio.ordered.review.service.DuplicateReviewException;
import pl.dybcio.ordered.review.service.ProductNotPurchasedException;
import pl.dybcio.ordered.user.service.EmailAlreadyTakenException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ProductNotFoundException.class)
  public ProblemDetail handleNotFound(ProductNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(EmailAlreadyTakenException.class)
  public ProblemDetail handleEmailAlreadyTaken(EmailAlreadyTakenException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
  }

  @ExceptionHandler(InsufficientStockException.class)
  public ProblemDetail handleInsufficientStock(InsufficientStockException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    problem.setTitle("Insufficient stock");
    return problem;
  }

  @ExceptionHandler(pl.dybcio.ordered.inventory.service.InsufficientStockException.class)
  public ProblemDetail handleInsufficientStockCheckout(
      pl.dybcio.ordered.inventory.service.InsufficientStockException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    problem.setTitle("Insufficient stock");
    return problem;
  }

  @ExceptionHandler(OrderNotFoundException.class)
  public ProblemDetail handleOrderNotFound(OrderNotFoundException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    problem.setTitle("Order not found");
    return problem;
  }

  @ExceptionHandler(InvalidOrderStatusTransitionException.class)
  public ProblemDetail handleInvalidTransition(InvalidOrderStatusTransitionException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    pd.setTitle("Invalid order status transition");
    return pd;
  }

  @ExceptionHandler(OrderStatusChangeNotAllowedException.class)
  public ProblemDetail handleStatusChangeNotAllowed(OrderStatusChangeNotAllowedException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    pd.setTitle("Order status change not allowed");
    return pd;
  }

  @ExceptionHandler(ProductOwnershipException.class)
  public ProblemDetail handleProductOwnership(ProductOwnershipException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    pd.setTitle("Product ownership violation");
    return pd;
  }

  @ExceptionHandler(PropertyReferenceException.class)
  public ProblemDetail handlePropertyReference(PropertyReferenceException ex) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST, "Invalid sort parameter: " + ex.getMessage());
  }

  @ExceptionHandler(ProductNotPurchasedException.class)
  public ProblemDetail handleProductNotPurchased(ProductNotPurchasedException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    pd.setTitle("Product not purchased");
    return pd;
  }

  @ExceptionHandler(DuplicateReviewException.class)
  public ProblemDetail handleDuplicateReview(DuplicateReviewException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    pd.setTitle("Duplicate review");
    return pd;
  }

  @ExceptionHandler(PaymentProcessingException.class)
  public ProblemDetail handlePaymentProcessing(PaymentProcessingException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    pd.setTitle("Payment processing failed");
    return pd;
  }

  @ExceptionHandler(AddressNotFoundException.class)
  public ProblemDetail handleAddressNotFound(AddressNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(CartItemNotFoundException.class)
  public ProblemDetail handleCartItemNotFound(CartItemNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(EmptyCartException.class)
  public ProblemDetail handleEmptyCart(EmptyCartException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(ProductNotActiveException.class)
  public ProblemDetail handleProductNotActive(ProductNotActiveException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    pd.setTitle("Product not available");
    return pd;
  }
}
