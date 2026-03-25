package dev.chan.transferservice.domain.event;

import dev.chan.transferservice.domain.transfer.TransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class TransferEventConsumer {

    private final TransferService transferService;

    @KafkaListener(topics = "transfer-events", groupId = "transfer-group")
    public void consume(TransferEvent event) {
        log.info("[Saga Consumer 수신] eventId: {}, status: {}", event.getEventId(), event.getStatus());

        try {
            switch (event.getStatus()) {
                case TransferEventEntity.STATUS_WITHDRAWN -> 
                    // 1단계(출금) 완료 시 -> 2단계(입금) 수행
                    transferService.deposit(event.getEventId(), event.getToAccountNumber(), event.getAmount());

                case TransferEventEntity.STATUS_DEPOSIT_FAILED -> 
                    // 2단계(입금) 실패 시 -> 보상 트랜잭션(환불) 수행
                    transferService.refund(event.getEventId());

                case TransferEventEntity.STATUS_COMPLETED -> 
                    log.info("Saga 완료 이벤트 수신 - eventId: {}", event.getEventId());

                case TransferEventEntity.STATUS_REFUNDED -> 
                    log.info("보상 트랜잭션(환불) 완료 이벤트 수신 - eventId: {}", event.getEventId());

                default -> 
                    log.warn("알 수 없는 Saga 상태: {}", event.getStatus());
            }
        } catch (Exception e) {
            log.error("Saga 단계 처리 중 오류 발생 - eventId: {}, 사유: {}", event.getEventId(), e.getMessage());
            // 에러가 발생하더라도 Outbox와 Relay가 상태를 보고 다시 이벤트를 던져줄 수 있도록 설계됨
        }
    }
}
