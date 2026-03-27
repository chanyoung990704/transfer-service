package dev.chan.transferservice.domain.payment;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_order_id", columnList = "orderId"),
    @Index(name = "idx_payment_key", columnList = "paymentKey")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderId;

    @Column(unique = true)
    private String paymentKey;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal canceledAmount = BigDecimal.ZERO; // 누적 취소 금액

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder
    public Payment(String orderId, BigDecimal amount) {
        this.orderId = orderId;
        this.amount = amount;
        this.status = PaymentStatus.READY;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void startApproval(String paymentKey) {
        if (this.status != PaymentStatus.READY) {
            throw new IllegalStateException("결제 준비 상태에서만 승인을 시작할 수 있습니다.");
        }
        this.paymentKey = paymentKey;
        this.status = PaymentStatus.IN_PROGRESS;
        this.updatedAt = LocalDateTime.now();
    }

    public void complete() {
        if (this.status != PaymentStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행 중인 결제만 완료할 수 있습니다.");
        }
        this.status = PaymentStatus.DONE;
        this.approvedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void abort() {
        this.status = PaymentStatus.ABORTED;
        this.updatedAt = LocalDateTime.now();
    }

    // 취소 로직 (전체 및 부분 취소 대응)
    public void cancel(BigDecimal cancelAmount) {
        if (this.status != PaymentStatus.DONE && this.status != PaymentStatus.PARTIAL_CANCELED) {
            throw new IllegalStateException("결제 완료 상태에서만 취소가 가능합니다.");
        }

        BigDecimal totalCancelTarget = this.canceledAmount.add(cancelAmount);
        
        if (totalCancelTarget.compareTo(this.amount) > 0) {
            throw new IllegalArgumentException("취소 요청 금액이 결제 잔액을 초과합니다.");
        }

        this.canceledAmount = totalCancelTarget;
        
        if (this.canceledAmount.compareTo(this.amount) == 0) {
            this.status = PaymentStatus.CANCELED;
        } else {
            this.status = PaymentStatus.PARTIAL_CANCELED;
        }
        
        this.updatedAt = LocalDateTime.now();
    }
}
