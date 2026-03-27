package dev.chan.transferservice.api;

import dev.chan.transferservice.api.dto.*;
import dev.chan.transferservice.domain.payment.PaymentService;
import dev.chan.transferservice.idempotency.IdempotentOperation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 결제 요청 데이터 초기 저장 (준비 단계)
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody PaymentCreateRequest request) {
        log.info("결제 생성 API - orderId: {}", request.getOrderId());
        PaymentResponse response = paymentService.createPayment(request.getOrderId(), request.getAmount());
        return ResponseEntity.ok(response);
    }

    /**
     * 결제 승인 API (위변조 검증 + PG 승인)
     * Header: Idempotency-Key 필수
     */
    @PostMapping("/confirm")
    @IdempotentOperation
    public ResponseEntity<PaymentResponse> confirmPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentConfirmRequest request) {
        
        log.info("결제 승인 API - orderId: {}, idempotencyKey: {}", request.getOrderId(), idempotencyKey);
        PaymentResponse response = paymentService.confirmPayment(request);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
