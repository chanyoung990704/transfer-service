package dev.chan.transferservice.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PendingEventReconciler {

    private final PendingEventRedisService pendingEventRedisService;
    private final TransferEventRepository transferEventRepository;

    /**
     * Redis ZSet 기반 정합성 보정 스케줄러
     * 30초 이상 완료되지 않은 이벤트를 찾아내어 조치
     */
    @Scheduled(fixedDelay = 10000) // 10초마다 실행
    public void reconcile() {
        if (!pendingEventRedisService.isHealthy()) {
            return;
        }

        // 30초 전을 타임아웃 기준으로 설정
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(30);
        List<String> expiredEventIds = pendingEventRedisService.getExpiredEvents(threshold, 100);

        if (expiredEventIds.isEmpty()) {
            return;
        }

        log.warn("Saga 타임아웃 감지 (30초 초과) - 대상 건수: {}", expiredEventIds.size());

        for (String eventId : expiredEventIds) {
            transferEventRepository.findById(eventId).ifPresentOrElse(event -> {
                log.info("타임아웃 이벤트 상태 확인 - eventId: {}, status: {}", eventId, event.getStatus());
                
                if (TransferEventEntity.STATUS_COMPLETED.equals(event.getStatus()) || 
                    TransferEventEntity.STATUS_REFUNDED.equals(event.getStatus())) {
                    // 이미 완료된 경우라면 Redis ZSet에서만 누락된 것이므로 제거
                    pendingEventRedisService.removePendingEvents(List.of(eventId));
                } else {
                    log.error("미처리 이벤트 발견 - 재발행 또는 수동 확인 필요: {}", eventId);
                    // 실제 환경에서는 여기서 Kafka 재발행이나 보상 트랜잭션을 강제 호출함
                }
            }, () -> {
                log.error("존재하지 않는 이벤트가 ZSet에 머물러 있음 - 제거: {}", eventId);
                pendingEventRedisService.removePendingEvents(List.of(eventId));
            });
        }
    }
}
