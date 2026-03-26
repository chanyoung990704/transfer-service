package dev.chan.transferservice.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import dev.chan.transferservice.domain.event.PendingEventRedisService;
import java.util.*;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class TransferEventRelay {

    private static final String TRANSFER_TOPIC = "transfer-events";
    private static final int BATCH_SIZE = 100;
    private final TransferEventRepository transferEventRepository;
    private final KafkaTemplate<String, TransferEvent> kafkaTemplate;
    private final PendingEventRedisService pendingEventRedisService;

    /**
     * Outbox Relay 스케줄러: 미발행 이벤트를 읽어 Kafka로 전송
     * 주기: 1초 (운영 환경에 따라 조절)
     */
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void relay() {
        List<String> candidateEventIds = null;
        boolean usingRedis = false;

        // 1. Attempt to fetch ordered IDs from Redis ZSET
        try {
            if (pendingEventRedisService.isHealthy()) {
                candidateEventIds = pendingEventRedisService.getNextBatch(BATCH_SIZE);
                usingRedis = true;
                log.debug("Fetched {} pending events from Redis ZSET", candidateEventIds.size());
            }
        } catch (Exception e) {
            log.warn("Redis polling failed, falling back to DB (error: {})", e.getMessage());
        }

        // 2. Fallback: fetch IDs from DB with explicit ORDER BY occurredAt ASC
        if (candidateEventIds == null || candidateEventIds.isEmpty()) {
            candidateEventIds = transferEventRepository.findTop100PendingEventIds();
            usingRedis = false;
            log.debug("Fetched {} pending events from DB fallback", candidateEventIds.size());
        }

        if (candidateEventIds.isEmpty()) {
            return; // nothing to do
        }

        // 3. Load full entities by IDs
        List<TransferEventEntity> fetched = transferEventRepository.findAllById(candidateEventIds);
        Map<String, TransferEventEntity> entityById = fetched.stream()
            .collect(toMap(TransferEventEntity::getEventId, e -> e));

        List<TransferEventEntity> unpublishedEvents = candidateEventIds.stream()
            .map(entityById::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (unpublishedEvents.isEmpty()) {
            return;
        }

        log.info("Outbox relay batch: size={}, source={}", unpublishedEvents.size(), usingRedis ? "Redis" : "DB");

        List<String> successfulIds = new ArrayList<>();

        // 4. Process each event
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
                successfulIds.add(eventEntity.getEventId());
                log.info("Kafka 발행 성공 및 아웃박스 업데이트 완료 - eventId: {}", eventEntity.getEventId());

            } catch (Exception e) {
                log.error("Kafka 발행 실패 - eventId: {}, 사유: {}", eventEntity.getEventId(), e.getMessage());
                // 실패 시 다음 스케줄 주기에 재시도
            }
        }

        // 5. Cleanup: Remove successfully processed IDs from ZSET if using Redis
        if (usingRedis && !successfulIds.isEmpty()) {
            try {
                pendingEventRedisService.removePendingEvents(successfulIds);
                log.debug("Removed {} successful events from Redis ZSET", successfulIds.size());
            } catch (Exception e) {
                log.error("Failed to remove processed IDs from Redis ZSET: count={}, error={}",
                          successfulIds.size(), e.getMessage());
                // These events may be re-sent next cycle; rely on consumer idempotency
            }
        }
    }
}
