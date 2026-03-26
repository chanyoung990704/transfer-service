package dev.chan.transferservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RedisSubscriber {

    /**
     * Redis 채널을 통해 메시지가 수신되었을 때 처리
     * @param message 수신된 이체 상태 또는 이벤트 정보
     */
    public void onMessage(String message) {
        log.info("[Redis Sub 수신] 실시간 이체 상태 알림: {}", message);
        // 실무에서는 여기서 WebSocket 전송이나 실시간 대시보드 업데이트 수행
    }
}
