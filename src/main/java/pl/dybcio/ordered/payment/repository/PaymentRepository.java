package pl.dybcio.ordered.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.dybcio.ordered.payment.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {}
