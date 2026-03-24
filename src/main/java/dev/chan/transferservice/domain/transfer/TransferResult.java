package dev.chan.transferservice.domain.transfer;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class TransferResult {
    private final String eventId;
    private final String fromAccountNumber;
    private final String toAccountNumber;
    private final BigDecimal amount;
    private final BigDecimal fromBalanceAfter;
    private final LocalDateTime completedAt;
}
