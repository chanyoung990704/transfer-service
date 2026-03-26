package dev.chan.transferservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisPublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 지정된 채널로 메시지를 발행
     */
    public void publish(String topic, Object message) {
        log.info("Redis Pub 발행 - Topic: {}, Message: {}", topic, message);
        redisTemplate.convertAndSend(topic, message);
    }
}
