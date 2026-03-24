package dev.chan.transferservice.domain.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TransferEventConsumer {

    @KafkaListener(topics = "transfer-events", groupId = "transfer-group")
    public void consume(TransferEvent event) {
        // 실무에서는 여기서 알림, 정산 트리거, 외부 시스템 연동 등 처리
        log.info("[Kafka 수신] 이체 이벤트 - eventId: {}, from: {} → to: {}, amount: {}, status: {}",
            event.getEventId(),
            event.getFromAccountNumber(),
            event.getToAccountNumber(),
            event.getAmount(),
            event.getStatus());
    }
}
