package dev.chan.transferservice.domain.event;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// Kafka 메시지로 직렬화되는 이벤트 DTO
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class TransferEvent {

    private String eventId;           // 이벤트 고유 ID
    private String idempotencyKey;    // 멱등성 키
    private String fromAccountNumber;
    private String toAccountNumber;
    private BigDecimal amount;
    private String status;            // COMPLETED / FAILED
    private String failReason;
    private LocalDateTime occurredAt;
}
