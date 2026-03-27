package dev.chan.transferservice.domain.payment;

import dev.chan.transferservice.api.dto.PaymentConfirmRequest;
import dev.chan.transferservice.api.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;

    /**
     * 결제 시작 (준비 단계)
     */
    @Transactional
    public PaymentResponse createPayment(String orderId, BigDecimal amount) {
        log.info("결제 생성 요청 - orderId: {}, amount: {}", orderId, amount);
        
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(amount)
                .build();
        
        paymentRepository.save(payment);
        
        return PaymentResponse.builder()
                .orderId(payment.getOrderId())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .message("결제가 준비되었습니다.")
                .build();
    }

    /**
     * 결제 승인 흐름 (위변조 검증 + PG 승인)
     */
    @Transactional
    public PaymentResponse confirmPayment(PaymentConfirmRequest request) {
        log.info("결제 승인 프로세스 시작 - orderId: {}", request.getOrderId());

        // 1. 주문 정보 조회
        Payment payment = paymentRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문입니다: " + request.getOrderId()));

        // 2. 위변조 검증: DB의 금액과 요청 금액 비교
        if (payment.getAmount().compareTo(request.getAmount()) != 0) {
            log.error("결제 금액 위변조 감지! DB: {}, 요청: {}", payment.getAmount(), request.getAmount());
            payment.abort();
            throw new IllegalStateException("결제 금액이 일치하지 않습니다. 승인이 거절되었습니다.");
        }

        // 3. 상태 전이: READY -> IN_PROGRESS
        payment.startApproval(request.getPaymentKey());

        try {
            // 4. PG사 승인 API 호출 시뮬레이션 (3-Party)
            simulatePgApproval(request);

            // 5. 승인 성공 처리
            payment.complete();

            // 6. Transactional Outbox: 결제 완료 이벤트 저장
            PaymentEventEntity event = PaymentEventEntity.builder()
                    .orderId(payment.getOrderId())
                    .paymentKey(payment.getPaymentKey())
                    .amount(payment.getAmount())
                    .eventType("PAYMENT_COMPLETED")
                    .build();
            paymentEventRepository.save(event);

            log.info("결제 승인 완료 - orderId: {}", payment.getOrderId());

            return PaymentResponse.builder()
                    .orderId(payment.getOrderId())
                    .paymentKey(payment.getPaymentKey())
                    .amount(payment.getAmount())
                    .status(payment.getStatus())
                    .approvedAt(payment.getApprovedAt())
                    .message("결제가 최종 성공했습니다.")
                    .build();

        } catch (Exception e) {
            log.error("PG 승인 중 오류 발생 - orderId: {}, 사유: {}", payment.getOrderId(), e.getMessage());
            payment.abort();
            throw e;
        }
    }

    private void simulatePgApproval(PaymentConfirmRequest request) {
        // 실제 운영 환경에서는 RestTemplate이나 WebClient로 PG사 API를 호출함
        log.info("PG사 승인 호출 중... (paymentKey: {})", request.getPaymentKey());
        
        // 1% 확률로 승인 실패 시뮬레이션
        if (Math.random() < 0.01) {
            throw new RuntimeException("PG사 일시적 장애로 승인이 거절되었습니다.");
        }
    }
}
