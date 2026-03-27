package dev.chan.transferservice;

import dev.chan.transferservice.api.dto.PaymentConfirmRequest;
import dev.chan.transferservice.api.dto.PaymentCancelRequest;
import dev.chan.transferservice.api.dto.PaymentResponse;
import dev.chan.transferservice.domain.payment.Payment;
import dev.chan.transferservice.domain.payment.PaymentRepository;
import dev.chan.transferservice.domain.payment.PaymentService;
import dev.chan.transferservice.domain.payment.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
public class PaymentIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    private String orderId;
    private final BigDecimal amount = new BigDecimal("10000.0000");

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        orderId = "ORDER-" + UUID.randomUUID();
        paymentService.createPayment(orderId, amount);
    }

    @Test
    @DisplayName("결제 승인 성공 시나리오: 위변조 검증 및 상태 전이 확인")
    void payment_confirm_success() {
        PaymentConfirmRequest confirmRequest = PaymentConfirmRequest.builder()
                .orderId(orderId)
                .paymentKey("pk_test_12345")
                .amount(amount)
                .build();

        PaymentResponse response = paymentService.confirmPayment(confirmRequest);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.DONE);
        Payment payment = paymentRepository.findByOrderId(orderId).get();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
    }

    @Test
    @DisplayName("결제 금액 불일치 시 승인 거절 시나리오")
    void payment_confirm_fail_amount_mismatch() {
        // given: 10000원 주문에 대해 5000원으로 승인 요청
        PaymentConfirmRequest confirmRequest = PaymentConfirmRequest.builder()
                .orderId(orderId)
                .paymentKey("pk_test_mismatch")
                .amount(new BigDecimal("5000.0000"))
                .build();

        // when & then: 위변조 예외 발생 확인
        assertThatThrownBy(() -> paymentService.confirmPayment(confirmRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("결제 금액이 일치하지 않습니다");

        // 트랜잭션 롤백으로 인해 상태는 READY로 유지되거나, 요구사항에 따라 달라질 수 있음
        Payment payment = paymentRepository.findByOrderId(orderId).get();
        assertThat(payment.getStatus()).isNotEqualTo(PaymentStatus.DONE);
    }

    @Test
    @DisplayName("결제 완료 후 부분 취소 시나리오")
    void payment_partial_cancel_success() {
        // given: 먼저 결제 완료
        PaymentConfirmRequest confirmRequest = PaymentConfirmRequest.builder()
                .orderId(orderId)
                .paymentKey("pk_test_cancel")
                .amount(amount)
                .build();
        paymentService.confirmPayment(confirmRequest);

        // when: 3000원 부분 취소
        PaymentCancelRequest cancelRequest = PaymentCancelRequest.builder()
                .paymentKey("pk_test_cancel")
                .cancelAmount(new BigDecimal("3000.0000"))
                .cancelReason("고객 변심")
                .build();
        PaymentResponse response = paymentService.cancelPayment(cancelRequest);

        // then
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
        Payment payment = paymentRepository.findByOrderId(orderId).get();
        assertThat(payment.getCanceledAmount()).isEqualByComparingTo("3000.0000");
    }
}
