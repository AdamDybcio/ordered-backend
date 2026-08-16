package pl.dybcio.ordered.payment.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.order.repository.OrderRepository;
import pl.dybcio.ordered.order.service.OrderService;
import pl.dybcio.ordered.payment.dto.PaymentResult;
import pl.dybcio.ordered.payment.entity.Payment;
import pl.dybcio.ordered.payment.entity.PaymentStatus;
import pl.dybcio.ordered.payment.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentRetrySchedulerTest {

  @Mock private PaymentRepository paymentRepository;
  @Mock private OrderRepository orderRepository;
  @Mock private PaymentService paymentService;
  @Mock private OrderService orderService;

  private PaymentRetryScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler =
        new PaymentRetryScheduler(paymentRepository, orderRepository, paymentService, orderService);
    setMaxRetryAttempts(scheduler, 5);
  }

  private void setMaxRetryAttempts(PaymentRetryScheduler s, int value) {
    try {
      var field = PaymentRetryScheduler.class.getDeclaredField("maxRetryAttempts");
      field.setAccessible(true);
      field.set(s, value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Order order(long id) {
    return Order.builder().id(id).totalAmount(new BigDecimal("50.00")).build();
  }

  private Payment payment(long id, Order order, int retryCount) {
    return Payment.builder()
        .id(id)
        .order(order)
        .status(PaymentStatus.PENDING_RETRY)
        .retryCount(retryCount)
        .amount(order.getTotalAmount())
        .build();
  }

  @Test
  void retryPendingPayments_retriesEligiblePayment_andAppliesResult() {
    Order order = order(1L);
    Payment paymentEntity = payment(10L, order, 2);

    when(paymentRepository.findByStatusAndRetryCountLessThan(PaymentStatus.PENDING_RETRY, 5))
        .thenReturn(List.of(paymentEntity));
    when(paymentRepository.findByStatusAndRetryCountGreaterThanEqual(
            PaymentStatus.PENDING_RETRY, 5))
        .thenReturn(List.of());
    when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

    PaymentResult successResult = PaymentResult.success(paymentEntity);
    when(paymentService.charge(order)).thenReturn(successResult);

    scheduler.retryPendingPayments();

    verify(paymentService).charge(order);
    verify(orderService).applyPaymentResult(1L, successResult);
    verify(orderService, never()).cancelDueToPaymentFailure(any());
  }

  @Test
  void retryPendingPayments_skipsPaymentWhoseOrderNoLongerExists() {
    Order order = order(2L);
    Payment paymentEntity = payment(11L, order, 1);

    when(paymentRepository.findByStatusAndRetryCountLessThan(PaymentStatus.PENDING_RETRY, 5))
        .thenReturn(List.of(paymentEntity));
    when(paymentRepository.findByStatusAndRetryCountGreaterThanEqual(
            PaymentStatus.PENDING_RETRY, 5))
        .thenReturn(List.of());
    when(orderRepository.findById(2L)).thenReturn(Optional.empty());

    scheduler.retryPendingPayments();

    verify(paymentService, never()).charge(any());
    verify(orderService, never()).applyPaymentResult(any(), any());
  }

  @Test
  void retryPendingPayments_exhaustedAttempts_cancelsOrderAndMarksFailed() {
    Order order = order(3L);
    Payment paymentEntity = payment(12L, order, 5);

    when(paymentRepository.findByStatusAndRetryCountLessThan(PaymentStatus.PENDING_RETRY, 5))
        .thenReturn(List.of());
    when(paymentRepository.findByStatusAndRetryCountGreaterThanEqual(
            PaymentStatus.PENDING_RETRY, 5))
        .thenReturn(List.of(paymentEntity));

    scheduler.retryPendingPayments();

    verify(orderService).cancelDueToPaymentFailure(3L);
    verify(paymentRepository).save(argThat(p -> p.getStatus() == PaymentStatus.FAILED));
    verify(paymentService, never()).charge(any());
  }

  @Test
  void retryPendingPayments_noPayments_doesNothing() {
    when(paymentRepository.findByStatusAndRetryCountLessThan(any(), anyInt()))
        .thenReturn(List.of());
    when(paymentRepository.findByStatusAndRetryCountGreaterThanEqual(any(), anyInt()))
        .thenReturn(List.of());

    scheduler.retryPendingPayments();

    verifyNoInteractions(paymentService, orderService, orderRepository);
  }
}
