package pl.dybcio.ordered.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import com.redis.testcontainers.RedisContainer;
import com.stripe.exception.CardException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.order.entity.OrderStatus;
import pl.dybcio.ordered.order.repository.OrderRepository;
import pl.dybcio.ordered.payment.entity.Payment;
import pl.dybcio.ordered.payment.entity.PaymentStatus;
import pl.dybcio.ordered.payment.repository.PaymentRepository;
import pl.dybcio.ordered.payment.service.PaymentRetryScheduler;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentRetrySchedulerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  @ServiceConnection(name = "redis")
  static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7.4-alpine"));

  @Autowired private PaymentRetryScheduler paymentRetryScheduler;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;

  @BeforeEach
  void setUp() {
    paymentRepository.deleteAll();
    orderRepository.deleteAll();
  }

  private Order savedOrder() {
    User buyer =
        userRepository.save(
            User.builder()
                .email("retry-test-" + System.nanoTime() + "@test.pl")
                .password("irrelevant")
                .firstName("Jan")
                .lastName("Testowy")
                .build());

    return orderRepository.save(
        Order.builder()
            .buyer(buyer)
            .status(OrderStatus.PAYMENT_PENDING)
            .totalAmount(new BigDecimal("77.00"))
            .build());
  }

  private Payment savedPayment(Order order, int retryCount) {
    return paymentRepository.save(
        Payment.builder()
            .order(order)
            .status(PaymentStatus.PENDING_RETRY)
            .retryCount(retryCount)
            .amount(order.getTotalAmount())
            .build());
  }

  @Test
  void eligiblePayment_retriedSuccessfully_confirmsOrder() {
    Order order = savedOrder();
    savedPayment(order, 2);

    PaymentIntent fakeIntent = new PaymentIntent();
    fakeIntent.setId("pi_retry_success");

    try (MockedStatic<PaymentIntent> mocked = mockStatic(PaymentIntent.class)) {
      mocked
          .when(
              () ->
                  PaymentIntent.create(
                      any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
          .thenReturn(fakeIntent);

      paymentRetryScheduler.retryPendingPayments();
    }

    Order updated = orderRepository.findById(order.getId()).orElseThrow();
    Payment updatedPayment = paymentRepository.findByOrderId(order.getId()).orElseThrow();

    assertThat(updated.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    assertThat(updatedPayment.getStripePaymentIntentId()).isEqualTo("pi_retry_success");
  }

  @Test
  void eligiblePayment_retryStillFails_incrementsRetryCount() {
    Order order = savedOrder();
    savedPayment(order, 2);

    try (MockedStatic<PaymentIntent> mocked = mockStatic(PaymentIntent.class)) {
      mocked
          .when(
              () ->
                  PaymentIntent.create(
                      any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
          .thenThrow(org.mockito.Mockito.mock(CardException.class));

      paymentRetryScheduler.retryPendingPayments();
    }

    Payment updatedPayment = paymentRepository.findByOrderId(order.getId()).orElseThrow();

    assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING_RETRY);
    assertThat(updatedPayment.getRetryCount()).isEqualTo(3);
  }

  @Test
  void exhaustedPayment_isCancelled_andMarkedFailed() {
    Order order = savedOrder();
    savedPayment(order, 5);

    paymentRetryScheduler.retryPendingPayments();

    Order updated = orderRepository.findById(order.getId()).orElseThrow();
    Payment updatedPayment = paymentRepository.findByOrderId(order.getId()).orElseThrow();

    assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
  }
}
