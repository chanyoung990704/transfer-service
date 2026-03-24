package dev.chan.transferservice.domain.transfer;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class TransferCommand {
    private final String idempotencyKey;
    private final String fromAccountNumber;
    private final String toAccountNumber;
    private final BigDecimal amount;
}
