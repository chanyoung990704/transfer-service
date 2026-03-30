package dev.chan.transferservice;

import dev.chan.transferservice.api.dto.PaymentConfirmRequest;
import dev.chan.transferservice.api.dto.PaymentResponse;
import dev.chan.transferservice.domain.payment.Payment;
import dev.chan.transferservice.domain.payment.PaymentRepository;
import dev.chan.transferservice.domain.payment.PaymentService;
import dev.chan.transferservice.domain.payment.PaymentStatus;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(TestRedisConfig.class)
@EmbeddedKafka(partitions = 1, brokerProperties = { "listeners=PLAINTEXT://localhost:9092", "port=9092" })
public class PaymentResilienceTest {

    @Autowired
    private PaymentService paymentService;

    @SpyBean
    private PaymentRepository paymentRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private String orderId;
    private final BigDecimal amount = new BigDecimal("10000.0000");

    @BeforeEach
    void setUp() {
        orderId = "ORDER-" + UUID.randomUUID();
        // 각 테스트 전 서킷 브레이커 초기화
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentConfirm");
        cb.reset();
    }

    @Test
    @DisplayName("외부 서비스 장애 시 Circuit Breaker가 작동하여 Fallback 응답을 반환한다")
    void circuit_breaker_opens_on_failure() {
        // given: DB 조회 시 강제로 예외 발생시켜 장애 상황 시뮬레이션
        doThrow(new RuntimeException("DB 연결 장애")).when(paymentRepository).findByOrderId(anyString());

        PaymentConfirmRequest request = PaymentConfirmRequest.builder()
                .orderId(orderId)
                .amount(amount)
                .paymentKey("pk_test")
                .build();

        // when: 지속적인 실패 발생 (최소 호출 수 5회)
        for (int i = 0; i < 6; i++) {
            PaymentResponse response = paymentService.confirmPayment(request);
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.ABORTED);
            assertThat(response.getMessage()).contains("장애 전파 차단");
        }

        // then: 서킷 브레이커 상태 확인
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentConfirm");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("정상 상황에서는 Fallback이 호출되지 않고 정상 응답을 반환한다")
    void normal_case_no_fallback() {
        // given: 정상 데이터 저장
        Payment payment = Payment.builder().orderId(orderId).amount(amount).build();
        doReturn(Optional.of(payment)).when(paymentRepository).findByOrderId(orderId);

        PaymentConfirmRequest request = PaymentConfirmRequest.builder()
                .orderId(orderId)
                .amount(amount)
                .paymentKey("pk_test_normal")
                .build();

        // when
        PaymentResponse response = paymentService.confirmPayment(request);

        // then
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(response.getMessage()).contains("결제가 최종 성공했습니다");
        
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentConfirm");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
