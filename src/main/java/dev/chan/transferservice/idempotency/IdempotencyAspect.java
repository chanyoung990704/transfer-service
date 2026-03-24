package dev.chan.transferservice.idempotency;

import dev.chan.transferservice.api.dto.TransferResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class IdempotencyAspect {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${transfer.idempotency.ttl-seconds:86400}")
    private long ttlSeconds;

    private static final String KEY_PREFIX = "idempotency:";
    private static final String PROCESSING = "PROCESSING";
    private static final String COMPLETED = "COMPLETED";

    // @IdempotentOperation 어노테이션이 붙은 메서드에 적용
    @Around("@annotation(dev.chan.transferservice.idempotency.IdempotentOperation)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request =
            ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();

        String idempotencyKey = request.getHeader("Idempotency-Key");

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key 헤더가 필요합니다.");
        }

        String redisKey = KEY_PREFIX + idempotencyKey;

        // 1. 이미 처리 중인 요청 감지
        IdempotencyKey cached = (IdempotencyKey) redisTemplate.opsForValue().get(redisKey);

        if (cached != null) {
            if (PROCESSING.equals(cached.getStatus())) {
                log.warn("중복 요청 감지 - 처리 중: {}", idempotencyKey);
                throw new IllegalStateException("동일한 요청이 처리 중입니다. 잠시 후 다시 시도하세요.");
            }
            if (COMPLETED.equals(cached.getStatus())) {
                log.info("멱등성 적용 - 캐시 응답 반환: {}", idempotencyKey);
                TransferResponse cachedResponse =
                    objectMapper.readValue(cached.getResponseJson(), TransferResponse.class);
                return ResponseEntity.ok(cachedResponse);
            }
        }

        // 2. PROCESSING 상태로 먼저 저장 (중복 처리 방지)
        IdempotencyKey processing = IdempotencyKey.builder()
            .key(idempotencyKey)
            .status(PROCESSING)
            .build();
        redisTemplate.opsForValue().set(redisKey, processing, Duration.ofSeconds(ttlSeconds));

        try {
            // 3. 실제 비즈니스 로직 실행
            Object result = joinPoint.proceed();

            // 4. 결과 캐싱 (COMPLETED 상태)
            if (result instanceof ResponseEntity<?> responseEntity) {
                String responseJson = objectMapper.writeValueAsString(responseEntity.getBody());
                IdempotencyKey completed = IdempotencyKey.builder()
                    .key(idempotencyKey)
                    .responseJson(responseJson)
                    .status(COMPLETED)
                    .build();
                redisTemplate.opsForValue().set(redisKey, completed, Duration.ofSeconds(ttlSeconds));
            }

            return result;
        } catch (Exception e) {
            // 실패 시 Redis에서 제거 → 재시도 허용
            redisTemplate.delete(redisKey);
            throw e;
        }
    }
}
