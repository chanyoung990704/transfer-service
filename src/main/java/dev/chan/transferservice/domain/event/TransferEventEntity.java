package dev.chan.transferservice.domain.event;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transfer_events",
      indexes = {
          @Index(name = "idx_idempotency_key", columnList = "idempotencyKey"),
          @Index(name = "idx_occurred_at", columnList = "occurredAt"),
          @Index(name = "idx_published", columnList = "published")
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

    // Outbox status
    @Column(nullable = false)
    private boolean published;

    private LocalDateTime publishedAt;

    @Builder
    public TransferEventEntity(String eventId, String idempotencyKey,
                               String fromAccountNumber, String toAccountNumber,
                               BigDecimal amount, String status,
                               String failReason, LocalDateTime occurredAt,
                               boolean published) {
        this.eventId = eventId;
        this.idempotencyKey = idempotencyKey;
        this.fromAccountNumber = fromAccountNumber;
        this.toAccountNumber = toAccountNumber;
        this.amount = amount;
        this.status = status;
        this.failReason = failReason;
        this.occurredAt = occurredAt;
        this.published = published;
    }

    // 메시지 발행 완료 시 호출
    public void markAsPublished() {
        this.published = true;
        this.publishedAt = LocalDateTime.now();
    }
}
