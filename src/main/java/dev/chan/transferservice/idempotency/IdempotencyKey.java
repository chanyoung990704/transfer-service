package dev.chan.transferservice.idempotency;

import lombok.*;
import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey implements Serializable {
    private String key;
    private String responseJson;  // 이미 처리된 응답 JSON 직렬화 보관
    private String status;        // PROCESSING / COMPLETED / FAILED
}
