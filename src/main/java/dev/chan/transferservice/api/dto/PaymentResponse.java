package dev.chan.transferservice.api.dto;

import dev.chan.transferservice.domain.payment.PaymentStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {
    private String orderId;
    private String paymentKey;
    private BigDecimal amount;
    private PaymentStatus status;
    private LocalDateTime approvedAt;
    private String message;
}
