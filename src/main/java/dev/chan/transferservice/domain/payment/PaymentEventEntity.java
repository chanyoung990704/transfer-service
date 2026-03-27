package dev.chan.transferservice.domain.payment;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_events", indexes = {
    @Index(name = "idx_payment_event_order_id", columnList = "orderId")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false)
    private String paymentKey;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private String eventType; // PAYMENT_COMPLETED, PAYMENT_CANCELED 등

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false)
    private boolean published;

    @Builder
    public PaymentEventEntity(String orderId, String paymentKey, BigDecimal amount, String eventType) {
        this.orderId = orderId;
        this.paymentKey = paymentKey;
        this.amount = amount;
        this.eventType = eventType;
        this.occurredAt = LocalDateTime.now();
        this.published = false;
    }

    public void markAsPublished() {
        this.published = true;
    }
}
