package dev.chan.transferservice.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PendingEventReconciler {

    private final TransferEventRepository transferEventRepository;
    private final PendingEventRedisService pendingEventRedisService;

    @Scheduled(fixedDelay = 30000)
    public void reconcile() {
        try {
            List<TransferEventEntity> pendingEvents = transferEventRepository.findTop1000PendingEvents();
            if (pendingEvents.isEmpty()) {
                return;
            }

            int addSuccessCount = 0;
            for (TransferEventEntity event : pendingEvents) {
                try {
                    pendingEventRedisService.addPendingEvent(event.getEventId(), event.getOccurredAt());
                    addSuccessCount++;
                } catch (Exception e) {
                    log.warn("Reconciler: failed to add event {} to ZSET: {}", event.getEventId(), e.getMessage());
                }
            }
            if (addSuccessCount > 0) {
                log.info("Reconciler: successfully backfilled {} pending events into Redis ZSET", addSuccessCount);
            }
        } catch (Exception e) {
            log.error("Reconciler execution error", e);
        }
    }
}