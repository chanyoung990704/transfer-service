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
     * Saga 1단계: 출금 (Withdrawal)
     * - 보상 트랜잭션을 위해 상태를 WITHDRAWN으로 기록하고 아웃박스 저장.
     */
    @Transactional
    public TransferResult transfer(TransferCommand command) {
        String eventId = UUID.randomUUID().toString();
        log.info("Saga 1단계 시작 [출금] - eventId: {}, from: {}, amount: {}", 
                 eventId, command.getFromAccountNumber(), command.getAmount());

        // 출금 계좌 비관적 락 조회
        Account fromAccount = accountRepository.findByAccountNumberWithLock(command.getFromAccountNumber())
            .orElseThrow(() -> new IllegalArgumentException("출금 계좌를 찾을 수 없습니다: " + command.getFromAccountNumber()));

        // 출금 수행
        fromAccount.withdraw(command.getAmount());

        // 아웃박스(Outbox) 테이블에 WITHDRAWN 상태로 저장
        TransferEventEntity eventEntity = TransferEventEntity.builder()
            .eventId(eventId)
            .idempotencyKey(command.getIdempotencyKey())
            .fromAccountNumber(command.getFromAccountNumber())
            .toAccountNumber(command.getToAccountNumber())
            .amount(command.getAmount())
            .status(TransferEventEntity.STATUS_WITHDRAWN) // 1단계 완료
            .occurredAt(LocalDateTime.now())
            .published(false)
            .build();
        transferEventRepository.save(eventEntity);

        // 감사 로그 기록
        auditLogRepository.save(AuditLog.builder()
            .action("SAGA_WITHDRAW_COMPLETE")
            .idempotencyKey(command.getIdempotencyKey())
            .fromAccount(command.getFromAccountNumber())
            .toAccount(command.getToAccountNumber())
            .detail("eventId=" + eventId + ", amount=" + command.getAmount())
            .result("SUCCESS")
            .build());

        return TransferResult.builder()
            .eventId(eventId)
            .fromAccountNumber(command.getFromAccountNumber())
            .toAccountNumber(command.getToAccountNumber())
            .amount(command.getAmount())
            .fromBalanceAfter(fromAccount.getBalance())
            .completedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Saga 2단계: 입금 (Deposit) - Consumer에 의해 호출됨
     */
    @Transactional
    public void deposit(String eventId, String toAccountNumber, java.math.BigDecimal amount) {
        log.info("Saga 2단계 시작 [입금] - eventId: {}, to: {}, amount: {}", eventId, toAccountNumber, amount);
        
        TransferEventEntity eventEntity = transferEventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalStateException("이벤트를 찾을 수 없습니다: " + eventId));

        if (TransferEventEntity.STATUS_COMPLETED.equals(eventEntity.getStatus())) {
            log.info("이미 완료된 이체입니다: {}", eventId);
            return;
        }

        try {
            // 입금 계좌 비관적 락 조회
            Account toAccount = accountRepository.findByAccountNumberWithLock(toAccountNumber)
                .orElseThrow(() -> new IllegalArgumentException("입금 계좌를 찾을 수 없습니다: " + toAccountNumber));

            // 입금 수행
            toAccount.deposit(amount);

            // 상태 업데이트: COMPLETED
            eventEntity.updateStatus(TransferEventEntity.STATUS_COMPLETED, null);
            log.info("Saga 전체 완료 - eventId: {}", eventId);

        } catch (Exception e) {
            log.error("입금 실패로 인한 보상 트랜잭션 유도 - eventId: {}, 사유: {}", eventId, e.getMessage());
            // 입금 실패 시 상태를 DEPOSIT_FAILED로 변경하여 Relay가 보상 트랜잭션 이벤트를 발행하게 함
            eventEntity.updateStatus(TransferEventEntity.STATUS_DEPOSIT_FAILED, e.getMessage());
            throw e; // 트랜잭션 롤백 및 에러 로그 확인용
        }
    }

    /**
     * Saga 보상 트랜잭션: 환불 (Refund) - 입금 실패 시 호출됨
     */
    @Transactional
    public void refund(String eventId) {
        TransferEventEntity eventEntity = transferEventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalStateException("이벤트를 찾을 수 없습니다: " + eventId));

        if (TransferEventEntity.STATUS_REFUNDED.equals(eventEntity.getStatus())) {
            log.info("이미 환불 처리된 이체입니다: {}", eventId);
            return;
        }

        log.info("보상 트랜잭션 시작 [환불] - eventId: {}, to: {}", eventId, eventEntity.getFromAccountNumber());

        Account fromAccount = accountRepository.findByAccountNumberWithLock(eventEntity.getFromAccountNumber())
            .orElseThrow(() -> new IllegalStateException("환불 계좌를 찾을 수 없습니다: " + eventEntity.getFromAccountNumber()));

        // 환불 수행 (입금과 동일한 로직)
        fromAccount.deposit(eventEntity.getAmount());

        // 상태 업데이트: REFUNDED
        eventEntity.updateStatus(TransferEventEntity.STATUS_REFUNDED, "Deposit failed, money refunded");
        
        log.info("보상 트랜잭션 완료 - eventId: {}", eventId);
    }
}
