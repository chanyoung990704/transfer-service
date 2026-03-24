package dev.chan.transferservice.domain.event;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transfer_events",
      indexes = {
          @Index(name = "idx_idempotency_key", columnList = "idempotencyKey"),
          @Index(name = "idx_occurred_at", columnList = "occurredAt")
      })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferEventEntity {

    @Id
    @Column(length = 36)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, length = 20)
    private String fromAccountNumber;

    @Column(nullable = false, length = 20)
    private String toAccountNumber;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 500)
    private String failReason;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Builder
    public TransferEventEntity(String eventId, String idempotencyKey,
                               String fromAccountNumber, String toAccountNumber,
                               BigDecimal amount, String status,
                               String failReason, LocalDateTime occurredAt) {
        this.eventId = eventId;
        this.idempotencyKey = idempotencyKey;
        this.fromAccountNumber = fromAccountNumber;
        this.toAccountNumber = toAccountNumber;
        this.amount = amount;
        this.status = status;
        this.failReason = failReason;
        this.occurredAt = occurredAt;
    }
}
