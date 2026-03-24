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
     * Outbox Relay 스케줄러: 미발행 이벤트를 읽어 Kafka로 전송
     * 주기: 1초 (운영 환경에 따라 조절)
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
                // Kafka 메시지 생성
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

                // Kafka 전송
                kafkaTemplate.send(TRANSFER_TOPIC, eventEntity.getEventId(), kafkaEvent);
                
                // 전송 성공 시 발행 완료 마킹
                eventEntity.markAsPublished();
                log.info("Kafka 발행 성공 및 아웃박스 업데이트 완료 - eventId: {}", eventEntity.getEventId());

            } catch (Exception e) {
                log.error("Kafka 발행 실패 - eventId: {}, 사유: {}", eventEntity.getEventId(), e.getMessage());
                // 실패 시 다음 스케줄 주기에 재시도
            }
        }
    }
}
