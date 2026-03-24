package dev.chan.transferservice.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferEventRepository extends JpaRepository<TransferEventEntity, String> {
}
