package dev.chan.transferservice.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PendingEventRedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PENDING_EVENTS_KEY = "transfer:pending-events";

    private double toEpochMilliSeconds(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public void addPendingEvent(String eventId, LocalDateTime occurredAt) {
        double score = toEpochMilliSeconds(occurredAt);
        try {
            redisTemplate.opsForZSet().add(PENDING_EVENTS_KEY, eventId, score);
        } catch (Exception e) {
            log.error("Failed to add event to Redis ZSET: eventId={}, error={}", eventId, e.getMessage());
        }
    }

    /**
     * 특정 시점(threshold) 이전의 만료된 이벤트들만 조회
     */
    public List<String> getExpiredEvents(LocalDateTime threshold, int limit) {
        double maxScore = toEpochMilliSeconds(threshold);
        try {
            Set<Object> membersObj = redisTemplate.opsForZSet().rangeByScore(PENDING_EVENTS_KEY, 0, maxScore, 0, limit);
            if (membersObj == null) return List.of();
            return membersObj.stream()
                .map(obj -> (String) obj)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch expired events from Redis ZSET: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 기존 Relay 코드 호환성을 위한 메서드
     */
    public List<String> getNextBatch(int limit) {
        try {
            Set<Object> membersObj = redisTemplate.opsForZSet().range(PENDING_EVENTS_KEY, 0L, (long) (limit - 1));
            if (membersObj == null) return List.of();
            return membersObj.stream()
                .map(obj -> (String) obj)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch batch from Redis ZSET: {}", e.getMessage());
            return List.of();
        }
    }

    public void removePendingEvents(Collection<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return;
        }
        try {
            // 가변 인자 경고 해결을 위해 Object 배열로 명시적 캐스팅
            redisTemplate.opsForZSet().remove(PENDING_EVENTS_KEY, (Object[]) eventIds.toArray(new String[0]));
        } catch (Exception e) {
            log.error("Failed to remove events from Redis ZSET: count={}, error={}", eventIds.size(), e.getMessage());
        }
    }

    public boolean isHealthy() {
        try {
            // RedisCallback 타입을 명시적으로 지정하여 모호성 해결
            Boolean healthy = redisTemplate.execute((RedisCallback<Boolean>) connection -> {
                connection.ping();
                return true;
            });
            return Boolean.TRUE.equals(healthy);
        } catch (Exception e) {
            return false;
        }
    }
}
