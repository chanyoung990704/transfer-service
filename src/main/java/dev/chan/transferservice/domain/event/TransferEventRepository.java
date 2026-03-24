package dev.chan.transferservice.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransferEventRepository extends JpaRepository<TransferEventEntity, String> {
    // 아직 발행되지 않은 이벤트 조회
    List<TransferEventEntity> findByPublishedFalse();
}
