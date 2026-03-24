package dev.chan.transferservice.domain.transfer;

import dev.chan.transferservice.audit.*;
import dev.chan.transferservice.domain.account.*;
import dev.chan.transferservice.domain.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferEventRepository transferEventRepository;
    private final AuditLogRepository auditLogRepository;

    /**
     * 계좌 이체 핵심 로직
     * - Transactional Outbox Pattern: DB 트랜잭션 내에서 비즈니스 로직과 아웃박스(이벤트)를 동시에 저장.
     * - 비동기 메시지 발행은 별도의 OutboxRelay가 처리.
     */
    @Transactional
    public TransferResult transfer(TransferCommand command) {
        String eventId = UUID.randomUUID().toString();
        log.info("이체 시작 - eventId: {}, from: {}, to: {}, amount: {}",
            eventId, command.getFromAccountNumber(), command.getToAccountNumber(), command.getAmount());

        // 감사 로그 - 요청 기록
        auditLogRepository.save(AuditLog.builder()
            .action("TRANSFER_REQUEST")
            .idempotencyKey(command.getIdempotencyKey())
            .fromAccount(command.getFromAccountNumber())
            .toAccount(command.getToAccountNumber())
            .detail("amount=" + command.getAmount())
            .result("PENDING")
            .build());

        // Pessimistic Lock으로 두 계좌 조회
        // 데드락 방지: 항상 accountNumber 오름차순으로 잠금 획득
        String first = command.getFromAccountNumber().compareTo(command.getToAccountNumber()) < 0
            ? command.getFromAccountNumber() : command.getToAccountNumber();
        String second = first.equals(command.getFromAccountNumber())
            ? command.getToAccountNumber() : command.getFromAccountNumber();

        Account firstAccount = accountRepository.findByAccountNumberWithLock(first)
            .orElseThrow(() -> new IllegalArgumentException("계좌를 찾을 수 없습니다: " + first));
        Account secondAccount = accountRepository.findByAccountNumberWithLock(second)
            .orElseThrow(() -> new IllegalArgumentException("계좌를 찾을 수 없습니다: " + second));

        Account fromAccount = first.equals(command.getFromAccountNumber()) ? firstAccount : secondAccount;
        Account toAccount = first.equals(command.getToAccountNumber()) ? firstAccount : secondAccount;

        // 출금 / 입금 (도메인 객체 내부에서 유효성 검사)
        fromAccount.withdraw(command.getAmount());
        toAccount.deposit(command.getAmount());

        // 아웃박스(Outbox) 테이블에 저장 (메시지 발행 원자성 보장)
        TransferEventEntity eventEntity = TransferEventEntity.builder()
            .eventId(eventId)
            .idempotencyKey(command.getIdempotencyKey())
            .fromAccountNumber(command.getFromAccountNumber())
            .toAccountNumber(command.getToAccountNumber())
            .amount(command.getAmount())
            .status("COMPLETED")
            .occurredAt(LocalDateTime.now())
            .published(false) // 아직 발행되지 않음
            .build();
        transferEventRepository.save(eventEntity);

        // 감사 로그 - 완료 기록
        auditLogRepository.save(AuditLog.builder()
            .action("TRANSFER_COMPLETE")
            .idempotencyKey(command.getIdempotencyKey())
            .fromAccount(command.getFromAccountNumber())
            .toAccount(command.getToAccountNumber())
            .detail("eventId=" + eventId + ", amount=" + command.getAmount())
            .result("SUCCESS")
            .build());

        log.info("DB 트랜잭션 완료 준비 (아웃박스 저장 완료) - eventId: {}", eventId);

        return TransferResult.builder()
            .eventId(eventId)
            .fromAccountNumber(command.getFromAccountNumber())
            .toAccountNumber(command.getToAccountNumber())
            .amount(command.getAmount())
            .fromBalanceAfter(fromAccount.getBalance())
            .completedAt(LocalDateTime.now())
            .build();
    }
}
