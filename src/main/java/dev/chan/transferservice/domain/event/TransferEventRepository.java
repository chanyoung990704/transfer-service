package dev.chan.transferservice.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;

public interface TransferEventRepository extends JpaRepository<TransferEventEntity, String> {
    // 아직 발행되지 않은 이벤트 조회
    List<TransferEventEntity> findByPublishedFalse();

    @Query(value = "SELECT e.event_id FROM transfer_events e WHERE e.published = false ORDER BY e.occurred_at ASC, e.event_id ASC LIMIT 100", nativeQuery = true)
    List<String> findTop100PendingEventIds();

    @Query(value = "SELECT e FROM transfer_events e WHERE e.published = false ORDER BY e.occurredAt ASC LIMIT 1000", nativeQuery = false)
    List<TransferEventEntity> findTop1000PendingEvents();
}
