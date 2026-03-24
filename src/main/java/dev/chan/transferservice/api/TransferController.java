package dev.chan.transferservice.api;

import dev.chan.transferservice.api.dto.*;
import dev.chan.transferservice.domain.transfer.*;
import dev.chan.transferservice.idempotency.IdempotentOperation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
@Slf4j
public class TransferController {

    private final TransferService transferService;

    /**
     * POST /api/v1/transfers
     * Header: Idempotency-Key: {uuid} (필수)
     */
    @PostMapping
    @IdempotentOperation  // AOP 멱등성 처리
    public ResponseEntity<TransferResponse> transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        log.info("이체 API 요청 - idempotencyKey: {}", idempotencyKey);

        TransferCommand command = TransferCommand.builder()
            .idempotencyKey(idempotencyKey)
            .fromAccountNumber(request.getFromAccountNumber())
            .toAccountNumber(request.getToAccountNumber())
            .amount(request.getAmount())
            .build();

        TransferResult result = transferService.transfer(command);

        return ResponseEntity.ok(TransferResponse.builder()
            .eventId(result.getEventId())
            .fromAccountNumber(result.getFromAccountNumber())
            .toAccountNumber(result.getToAccountNumber())
            .amount(result.getAmount())
            .fromBalanceAfter(result.getFromBalanceAfter())
            .completedAt(result.getCompletedAt())
            .message("이체가 완료되었습니다.")
            .build());
    }

    // 글로벌 예외 처리
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
