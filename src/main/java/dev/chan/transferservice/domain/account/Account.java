package dev.chan.transferservice.domain.account;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "accounts",
      indexes = @Index(name = "idx_account_number", columnList = "accountNumber"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Column(nullable = false)
    private String ownerName;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    // Pessimistic Lock: SELECT ... FOR UPDATE 시 버전 관리 (낙관적 락과 병행 가능)
    @Version
    private Long version;

    @Builder
    public Account(String accountNumber, String ownerName, BigDecimal balance) {
        this.accountNumber = accountNumber;
        this.ownerName = ownerName;
        this.balance = balance;
    }

    // 출금: 잔액 부족 시 예외
    public void withdraw(BigDecimal amount) {
        if (this.balance.compareTo(amount) < 0) {
            throw new IllegalStateException(
                "잔액 부족 - 계좌: %s, 잔액: %s, 요청금액: %s"
                    .formatted(accountNumber, balance, amount));
        }
        this.balance = this.balance.subtract(amount);
    }

    // 입금
    public void deposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("입금 금액은 0보다 커야 합니다.");
        }
        this.balance = this.balance.add(amount);
    }
}
