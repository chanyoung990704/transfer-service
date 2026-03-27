package dev.chan.transferservice.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class TransferEventRelay {

    private static final String TRANSFER_TOPIC = "transfer-events";
    private final TransferEventRepository transferEventRepository;
    private final KafkaTemplate<String, TransferEvent> kafkaTemplate;

    /**
     * Outbox Relay 스케줄러
     * - 멀티 인스턴스 중복 실행 방지를 위해 ShedLock 적용
     */
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void relay() {
        List<TransferEventEntity> unpublishedEvents = transferEventRepository.findByPublishedFalse();

        if (unpublishedEvents.isEmpty()) {
            return;
        }

        log.info("아웃박스 메시지 발행 시작 - 대상 건수: {}", unpublishedEvents.size());

        for (TransferEventEntity eventEntity : unpublishedEvents) {
            try {
                TransferEvent kafkaEvent = TransferEvent.builder()
                    .eventId(eventEntity.getEventId())
                    .idempotencyKey(eventEntity.getIdempotencyKey())
                    .fromAccountNumber(eventEntity.getFromAccountNumber())
                    .toAccountNumber(eventEntity.getToAccountNumber())
                    .amount(eventEntity.getAmount())
                    .status(eventEntity.getStatus())
                    .failReason(eventEntity.getFailReason())
                    .occurredAt(eventEntity.getOccurredAt())
                    .build();

                kafkaTemplate.send(TRANSFER_TOPIC, eventEntity.getEventId(), kafkaEvent);
                
                eventEntity.markAsPublished();
                log.info("Kafka 발행 성공 및 아웃박스 업데이트 완료 - eventId: {}", eventEntity.getEventId());

            } catch (Exception e) {
                log.error("Kafka 발행 실패 - eventId: {}, 사유: {}", eventEntity.getEventId(), e.getMessage());
            }
        }
    }
}
