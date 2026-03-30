package dev.chan.transferservice.domain.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentEventRepository extends JpaRepository<PaymentEventEntity, Long> {
    List<PaymentEventEntity> findByPublishedFalse();
    Optional<PaymentEventEntity> findByOrderId(String orderId);
}
