package dev.chan.transferservice.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            throw e;
        }
    }

    public List<String> getNextBatch(int limit) {
        try {
            Set<Object> membersObj = redisTemplate.opsForZSet().range(PENDING_EVENTS_KEY, 0L, (long) (limit - 1));
            if (membersObj == null) return List.of();
            return membersObj.stream()
                .map(obj -> (String) obj)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch batch from Redis ZSET: {}", e.getMessage());
            throw e;
        }
    }

    public void removePendingEvents(Collection<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return;
        }
        try {
            redisTemplate.opsForZSet().remove(PENDING_EVENTS_KEY, eventIds.toArray(new String[0]));
        } catch (Exception e) {
            log.error("Failed to remove events from Redis ZSET: count={}, error={}", eventIds.size(), e.getMessage());
            throw e;
        }
    }

    public boolean isHealthy() {
        try {
            Boolean healthy = redisTemplate.execute(connection -> {
                connection.ping();
                return Boolean.TRUE;
            });
            return Boolean.TRUE.equals(healthy);
        } catch (Exception e) {
            log.debug("Redis health check failed: {}", e.getMessage());
            return false;
        }
    }
}