package dev.chan.transferservice.domain.payment;

import dev.chan.transferservice.api.dto.PaymentConfirmRequest;
import dev.chan.transferservice.api.dto.PaymentCancelRequest;
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

    @Transactional
    public PaymentResponse createPayment(String orderId, BigDecimal amount) {
        log.info("결제 생성 요청 - orderId: {}, amount: {}", orderId, amount);
        Payment payment = Payment.builder().orderId(orderId).amount(amount).build();
        paymentRepository.save(payment);
        return PaymentResponse.builder().orderId(payment.getOrderId()).amount(payment.getAmount()).status(payment.getStatus()).message("결제가 준비되었습니다.").build();
    }

    @Transactional
    public PaymentResponse confirmPayment(PaymentConfirmRequest request) {
        log.info("결제 승인 프로세스 시작 - orderId: {}", request.getOrderId());
        Payment payment = paymentRepository.findByOrderId(request.getOrderId()).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문입니다: " + request.getOrderId()));
        if (payment.getAmount().compareTo(request.getAmount()) != 0) {
            payment.abort();
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

    /**
     * 결제 취소 흐름 (전체/부분 취소)
     */
    @Transactional
    public PaymentResponse cancelPayment(PaymentCancelRequest request) {
        log.info("결제 취소 요청 - paymentKey: {}, amount: {}", request.getPaymentKey(), request.getCancelAmount());

        // 1. 결제 내역 조회
        Payment payment = paymentRepository.findByPaymentKey(request.getPaymentKey())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 결제 정보입니다."));

        // 2. 외부 PG사 취소 API 호출 시뮬레이션
        simulatePgCancel(request);

        // 3. DB 상태 업데이트 (엔티티 내에서 금액 검증 및 상태 전이)
        payment.cancel(request.getCancelAmount());

        // 4. Transactional Outbox: 취소 이벤트 저장
        PaymentEventEntity event = PaymentEventEntity.builder()
                .orderId(payment.getOrderId())
                .paymentKey(payment.getPaymentKey())
                .amount(request.getCancelAmount())
                .eventType(payment.getStatus() == PaymentStatus.CANCELED ? "PAYMENT_FULLY_CANCELED" : "PAYMENT_PARTIALLY_CANCELED")
                .build();
        paymentEventRepository.save(event);

        log.info("결제 취소 완료 - orderId: {}, newStatus: {}", payment.getOrderId(), payment.getStatus());

        return PaymentResponse.builder()
                .orderId(payment.getOrderId())
                .paymentKey(payment.getPaymentKey())
                .amount(payment.getAmount().subtract(payment.getCanceledAmount())) // 취소 후 잔액 반환
                .status(payment.getStatus())
                .message("결제 취소가 완료되었습니다. (사유: " + request.getCancelReason() + ")")
                .build();
    }

    private void simulatePgApproval(PaymentConfirmRequest request) {
        log.info("PG사 승인 호출 중...");
        if (Math.random() < 0.01) throw new RuntimeException("PG사 승인 실패");
    }

    private void simulatePgCancel(PaymentCancelRequest request) {
        log.info("PG사 취소 API 호출 중... (cancelAmount: {})", request.getCancelAmount());
        // 취소 실패 시뮬레이션
        if (Math.random() < 0.01) {
            throw new RuntimeException("PG사 통신 장애로 취소가 실패했습니다.");
        }
    }
}
