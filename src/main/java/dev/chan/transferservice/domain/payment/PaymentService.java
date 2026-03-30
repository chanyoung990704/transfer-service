package dev.chan.transferservice.domain.payment;

import dev.chan.transferservice.api.dto.PaymentConfirmRequest;
import dev.chan.transferservice.api.dto.PaymentCancelRequest;
import dev.chan.transferservice.api.dto.PaymentResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;

    @Transactional
    public PaymentResponse createPayment(String orderId, BigDecimal amount) {
        log.info("결제 생성 요청 - orderId: {}, amount: {}", orderId, amount);
        Payment payment = Payment.builder().orderId(orderId).amount(amount).build();
        paymentRepository.save(payment);
        return PaymentResponse.builder().orderId(payment.getOrderId()).amount(payment.getAmount()).status(payment.getStatus()).message("결제가 준비되었습니다.").build();
    }

    @Transactional
    @CircuitBreaker(name = "paymentConfirm", fallbackMethod = "confirmPaymentFallback")
    @Retry(name = "paymentConfirm")
    public PaymentResponse confirmPayment(PaymentConfirmRequest request) {
        log.info("결제 승인 프로세스 시작 - orderId: {}", request.getOrderId());
        Payment payment = paymentRepository.findByOrderId(request.getOrderId()).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문입니다: " + request.getOrderId()));
        
        // 위변조 검증
        if (payment.getAmount().compareTo(request.getAmount()) != 0) {
            payment.abort(); // 상태 변경
            // 명시적으로 저장하여 트랜잭션 종료 시 반영되도록 함 (또는 예외 발생 전 flush)
            paymentRepository.save(payment);
            throw new IllegalStateException("결제 금액이 일치하지 않습니다.");
        }
        
        payment.startApproval(request.getPaymentKey());
        try {
            simulatePgApproval(request);
            payment.complete();
            paymentEventRepository.save(PaymentEventEntity.builder().orderId(payment.getOrderId()).paymentKey(payment.getPaymentKey()).amount(payment.getAmount()).eventType("PAYMENT_COMPLETED").build());
            return PaymentResponse.builder().orderId(payment.getOrderId()).paymentKey(payment.getPaymentKey()).amount(payment.getAmount()).status(payment.getStatus()).approvedAt(payment.getApprovedAt()).message("결제가 최종 성공했습니다.").build();
        } catch (Exception e) {
            payment.abort();
            throw e;
        }
    }

    public PaymentResponse confirmPaymentFallback(PaymentConfirmRequest request, Throwable t) {
        log.error("결제 승인 중 장애 발생 (Fallback 실행) - orderId: {}, 사유: {}", request.getOrderId(), t.getMessage());
        return PaymentResponse.builder()
                .orderId(request.getOrderId())
                .status(PaymentStatus.ABORTED)
                .message("현재 결제 서비스가 원활하지 않습니다. 나중에 다시 시도해 주세요. (장애 전파 차단)")
                .build();
    }

    @Transactional
    public PaymentResponse cancelPayment(PaymentCancelRequest request) {
        log.info("결제 취소 요청 - paymentKey: {}, amount: {}", request.getPaymentKey(), request.getCancelAmount());
        Payment payment = paymentRepository.findByPaymentKey(request.getPaymentKey())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 결제 정보입니다."));

        simulatePgCancel(request);
        payment.cancel(request.getCancelAmount());

        paymentEventRepository.save(PaymentEventEntity.builder()
                .orderId(payment.getOrderId())
                .paymentKey(payment.getPaymentKey())
                .amount(request.getCancelAmount())
                .eventType(payment.getStatus() == PaymentStatus.CANCELED ? "PAYMENT_FULLY_CANCELED" : "PAYMENT_PARTIALLY_CANCELED")
                .build());

        return PaymentResponse.builder()
                .orderId(payment.getOrderId())
                .paymentKey(payment.getPaymentKey())
                .amount(payment.getAmount().subtract(payment.getCanceledAmount()))
                .status(payment.getStatus())
                .message("결제 취소가 완료되었습니다.")
                .build();
    }

    private void simulatePgApproval(PaymentConfirmRequest request) {
        log.info("PG사 승인 호출 중... (성공 가정)");
        // 테스트 안정성을 위해 랜덤 예외 제거
    }

    private void simulatePgCancel(PaymentCancelRequest request) {
        log.info("PG사 취소 API 호출 중... (성공 가정)");
        // 테스트 안정성을 위해 랜덤 예외 제거
    }
}
