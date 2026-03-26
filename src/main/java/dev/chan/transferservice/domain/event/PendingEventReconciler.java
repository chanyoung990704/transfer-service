package dev.chan.transferservice.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
     * - 멀티 인스턴스 중복 실행 방지를 위해 ShedLock 적용
     */
    @Scheduled(fixedDelay = 10000)
    @SchedulerLock(name = "pendingEventReconcileLock", lockAtLeastFor = "5s", lockAtMostFor = "30s")
    public void reconcile() {
        if (!pendingEventRedisService.isHealthy()) {
            return;
        }

        LocalDateTime threshold = LocalDateTime.now().minusSeconds(30);
        List<String> expiredEventIds = pendingEventRedisService.getExpiredEvents(threshold, 100);

        if (expiredEventIds.isEmpty()) {
            return;
        }

        log.warn("Saga 타임아웃 감지 - 대상 건수: {}", expiredEventIds.size());

        for (String eventId : expiredEventIds) {
            transferEventRepository.findById(eventId).ifPresentOrElse(event -> {
                if (TransferEventEntity.STATUS_COMPLETED.equals(event.getStatus()) || 
                    TransferEventEntity.STATUS_REFUNDED.equals(event.getStatus())) {
                    pendingEventRedisService.removePendingEvents(List.of(eventId));
                } else {
                    log.error("미처리 이벤트 발견: {}", eventId);
                }
            }, () -> {
                log.error("존재하지 않는 이벤트 제거: {}", eventId);
                pendingEventRedisService.removePendingEvents(List.of(eventId));
            });
        }
    }
}
