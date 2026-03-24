package dev.chan.transferservice.api.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferResponse {
    private String eventId;
    private String fromAccountNumber;
    private String toAccountNumber;
    private BigDecimal amount;
    private BigDecimal fromBalanceAfter;
    private LocalDateTime completedAt;
    private String message;
}
