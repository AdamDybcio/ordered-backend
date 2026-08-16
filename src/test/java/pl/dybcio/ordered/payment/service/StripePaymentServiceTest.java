package pl.dybcio.ordered.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.payment.entity.Payment;
import pl.dybcio.ordered.payment.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class StripePaymentServiceTest {

  @Mock private PaymentRepository paymentRepository;
  @InjectMocks private StripePaymentService stripePaymentService;

  @Test
  void charge_success_savesPaymentWithSucceededStatus() {
    PaymentIntent fakeIntent = new PaymentIntent();
    fakeIntent.setId("pi_unit_test");

    Order order = Order.builder().id(1L).totalAmount(new BigDecimal("50.00")).build();

    when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

    try (MockedStatic<PaymentIntent> mocked = mockStatic(PaymentIntent.class)) {
      mocked
          .when(
              () ->
                  PaymentIntent.create(
                      any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
          .thenReturn(fakeIntent);

      var result = stripePaymentService.charge(order);

      assertThat(result.isSuccess()).isTrue();
      verify(paymentRepository).save(any(Payment.class));
    }
  }
}
