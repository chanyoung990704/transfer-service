package dev.chan.transferservice.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface TransferEventRepository extends JpaRepository<TransferEventEntity, String> {
    
    // 아직 발행되지 않은 이벤트 조회
    List<TransferEventEntity> findByPublishedFalse();

    @Query("SELECT e.eventId FROM TransferEventEntity e WHERE e.published = false ORDER BY e.occurredAt ASC")
    List<String> findTop100PendingEventIds();

    @Query("SELECT e FROM TransferEventEntity e WHERE e.published = false ORDER BY e.occurredAt ASC")
    List<TransferEventEntity> findTop1000PendingEvents();
}
