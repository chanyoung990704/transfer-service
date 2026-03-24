package dev.chan.transferservice.audit;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs",
      indexes = @Index(name = "idx_created_at", columnList = "createdAt"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String action;           // TRANSFER_REQUEST, TRANSFER_COMPLETE, etc.

    @Column(length = 100)
    private String idempotencyKey;

    @Column(length = 20)
    private String fromAccount;

    @Column(length = 20)
    private String toAccount;

    @Column(length = 500)
    private String detail;

    @Column(nullable = false, length = 20)
    private String result;           // SUCCESS / FAILURE

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public AuditLog(String action, String idempotencyKey,
                    String fromAccount, String toAccount,
                    String detail, String result) {
        this.action = action;
        this.idempotencyKey = idempotencyKey;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.detail = detail;
        this.result = result;
        this.createdAt = LocalDateTime.now();
    }
}
