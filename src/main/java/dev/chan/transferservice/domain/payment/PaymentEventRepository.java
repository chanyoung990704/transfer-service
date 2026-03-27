package dev.chan.transferservice.domain.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentEventRepository extends JpaRepository<PaymentEventEntity, Long> {
    List<PaymentEventEntity> findByPublishedFalse();
}
