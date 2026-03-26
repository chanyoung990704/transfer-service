package dev.chan.transferservice.domain.transfer;

import dev.chan.transferservice.audit.*;
import dev.chan.transferservice.config.RedisConfig;
import dev.chan.transferservice.config.RedisPublisher;
import dev.chan.transferservice.domain.account.*;
import dev.chan.transferservice.domain.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferEventRepository transferEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final RedisPublisher redisPublisher;
    private final PendingEventRedisService pendingEventRedisService;

    /**
     * Saga 1단계: 출금 (Withdrawal)
     * - Redis ZSet에 타임아웃 관리를 위해 등록
     * - Redis Pub으로 실시간 상태 발행
     */
    @Transactional
    public TransferResult transfer(TransferCommand command) {
        String eventId = UUID.randomUUID().toString();
        log.info("Saga 1단계 시작 [출금] - eventId: {}, from: {}, amount: {}", 
                 eventId, command.getFromAccountNumber(), command.getAmount());

        Account fromAccount = accountRepository.findByAccountNumberWithLock(command.getFromAccountNumber())
            .orElseThrow(() -> new IllegalArgumentException("출금 계좌를 찾을 수 없습니다: " + command.getFromAccountNumber()));

        fromAccount.withdraw(command.getAmount());

        TransferEventEntity eventEntity = TransferEventEntity.builder()
            .eventId(eventId)
            .idempotencyKey(command.getIdempotencyKey())
            .fromAccountNumber(command.getFromAccountNumber())
            .toAccountNumber(command.getToAccountNumber())
            .amount(command.getAmount())
            .status(TransferEventEntity.STATUS_WITHDRAWN)
            .occurredAt(LocalDateTime.now())
            .published(false)
            .build();
        transferEventRepository.save(eventEntity);

        // [Redis] SortedSet에 등록 (타임아웃 감시용)
        pendingEventRedisService.addPendingEvent(eventId, LocalDateTime.now());

        // [Redis] Pub 발행 (실시간 모니터링용)
        redisPublisher.publish(RedisConfig.TRANSFER_STATUS_TOPIC, 
            "[STARTED] 이체 시작: " + eventId + " (From: " + command.getFromAccountNumber() + ")");

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
     * Saga 2단계: 입금 (Deposit)
     */
    @Transactional
    public void deposit(String eventId, String toAccountNumber, java.math.BigDecimal amount) {
        log.info("Saga 2단계 시작 [입금] - eventId: {}, to: {}, amount: {}", eventId, toAccountNumber, amount);
        
        TransferEventEntity eventEntity = transferEventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalStateException("이벤트를 찾을 수 없습니다: " + eventId));

        if (TransferEventEntity.STATUS_COMPLETED.equals(eventEntity.getStatus())) {
            pendingEventRedisService.removePendingEvents(Collections.singletonList(eventId));
            return;
        }

        try {
            Account toAccount = accountRepository.findByAccountNumberWithLock(toAccountNumber)
                .orElseThrow(() -> new IllegalArgumentException("입금 계좌를 찾을 수 없습니다: " + toAccountNumber));

            toAccount.deposit(amount);
            eventEntity.updateStatus(TransferEventEntity.STATUS_COMPLETED, null);

            // [Redis] SortedSet에서 제거 (성공했으므로 더 이상 타임아웃 대상 아님)
            pendingEventRedisService.removePendingEvents(Collections.singletonList(eventId));

            // [Redis] Pub 발행
            redisPublisher.publish(RedisConfig.TRANSFER_STATUS_TOPIC, "[COMPLETED] 이체 완료: " + eventId);

            log.info("Saga 전체 완료 - eventId: {}", eventId);

        } catch (Exception e) {
            log.error("입금 실패로 인한 보상 트랜잭션 유도 - eventId: {}, 사유: {}", eventId, e.getMessage());
            eventEntity.updateStatus(TransferEventEntity.STATUS_DEPOSIT_FAILED, e.getMessage());
            
            // [Redis] Pub 발행
            redisPublisher.publish(RedisConfig.TRANSFER_STATUS_TOPIC, "[FAILED] 입금 실패: " + eventId + ", 사유: " + e.getMessage());
            
            throw e;
        }
    }

    /**
     * Saga 보상 트랜잭션: 환불 (Refund)
     */
    @Transactional
    public void refund(String eventId) {
        TransferEventEntity eventEntity = transferEventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalStateException("이벤트를 찾을 수 없습니다: " + eventId));

        if (TransferEventEntity.STATUS_REFUNDED.equals(eventEntity.getStatus())) {
            pendingEventRedisService.removePendingEvents(Collections.singletonList(eventId));
            return;
        }

        log.info("보상 트랜잭션 시작 [환불] - eventId: {}, to: {}", eventId, eventEntity.getFromAccountNumber());

        Account fromAccount = accountRepository.findByAccountNumberWithLock(eventEntity.getFromAccountNumber())
            .orElseThrow(() -> new IllegalStateException("환불 계좌를 찾을 수 없습니다: " + eventEntity.getFromAccountNumber()));

        fromAccount.deposit(eventEntity.getAmount());
        eventEntity.updateStatus(TransferEventEntity.STATUS_REFUNDED, "Deposit failed, money refunded");

        // [Redis] SortedSet에서 제거 (보상 완료되었으므로 제거)
        pendingEventRedisService.removePendingEvents(Collections.singletonList(eventId));

        // [Redis] Pub 발행
        redisPublisher.publish(RedisConfig.TRANSFER_STATUS_TOPIC, "[REFUNDED] 보상 트랜잭션 완료: " + eventId);
        
        log.info("보상 트랜잭션 완료 - eventId: {}", eventId);
    }
}
