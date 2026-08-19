package pl.dybcio.ordered.checkout.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.dybcio.ordered.checkout.dto.CheckoutReservationRequest;
import pl.dybcio.ordered.checkout.dto.CheckoutReservationResponse;
import pl.dybcio.ordered.checkout.service.CheckoutService;

@RestController
@RequestMapping("/internal/v1/checkout")
@RequiredArgsConstructor
public class CheckoutController {

  private final CheckoutService checkoutService;

  @PostMapping("/reserve")
  public ResponseEntity<CheckoutReservationResponse> reserve(
      @Valid @RequestBody CheckoutReservationRequest request) {
    CheckoutReservationResponse response = checkoutService.reserveForCheckout(request.buyerId());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/{reservationId}/release")
  public ResponseEntity<Void> release(@PathVariable UUID reservationId) {
    checkoutService.release(reservationId);
    return ResponseEntity.noContent().build();
  }
}
