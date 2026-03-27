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
    private String paymentKey; // PG사 발행 키

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

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

    // 결제 승인 프로세스 시작 (금액 검증 단계)
    public void startApproval(String paymentKey) {
        if (this.status != PaymentStatus.READY) {
            throw new IllegalStateException("결제 준비 상태에서만 승인을 시작할 수 있습니다. 현재 상태: " + this.status);
        }
        this.paymentKey = paymentKey;
        this.status = PaymentStatus.IN_PROGRESS;
        this.updatedAt = LocalDateTime.now();
    }

    // 결제 완료 처리
    public void complete() {
        if (this.status != PaymentStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행 중인 결제만 완료할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.DONE;
        this.approvedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 결제 실패/중단 처리
    public void abort() {
        this.status = PaymentStatus.ABORTED;
        this.updatedAt = LocalDateTime.now();
    }

    // 결제 취소 처리
    public void cancel() {
        if (this.status != PaymentStatus.DONE) {
            throw new IllegalStateException("완료된 결제만 취소할 수 있습니다.");
        }
        this.status = PaymentStatus.CANCELED;
        this.updatedAt = LocalDateTime.now();
    }
}
