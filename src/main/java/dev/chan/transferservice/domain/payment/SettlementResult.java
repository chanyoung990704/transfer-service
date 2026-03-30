package dev.chan.transferservice.domain.payment;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SettlementResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orderId;
    private BigDecimal paymentAmount;
    private BigDecimal eventAmount;
    private String status; // MATCH, MISMATCH, MISSING_EVENT

    private LocalDateTime settlementDate;

    public static SettlementResult match(String orderId, BigDecimal amount) {
        return SettlementResult.builder()
                .orderId(orderId)
                .paymentAmount(amount)
                .eventAmount(amount)
                .status("MATCH")
                .settlementDate(LocalDateTime.now())
                .build();
    }

    public static SettlementResult mismatch(String orderId, BigDecimal pAmount, BigDecimal eAmount) {
        return SettlementResult.builder()
                .orderId(orderId)
                .paymentAmount(pAmount)
                .eventAmount(eAmount)
                .status("MISMATCH")
                .settlementDate(LocalDateTime.now())
                .build();
    }
}
