package dev.chan.transferservice.domain.transfer;

import dev.chan.transferservice.domain.account.*;
import dev.chan.transferservice.domain.event.*;
import dev.chan.transferservice.audit.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock TransferEventRepository transferEventRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock KafkaTemplate<String, TransferEvent> kafkaTemplate;

    @InjectMocks TransferService transferService;

    Account fromAccount;
    Account toAccount;

    @BeforeEach
    void setUp() {
        fromAccount = Account.builder()
            .accountNumber("1000000001")
            .ownerName("홍길동")
            .balance(new BigDecimal("500000"))
            .build();
        toAccount = Account.builder()
            .accountNumber("2000000002")
            .ownerName("이순신")
            .balance(new BigDecimal("100000"))
            .build();
    }

    @Test
    @DisplayName("정상 이체 - 잔액 차감 및 증가 확인")
    void transfer_success() {
        when(accountRepository.findByAccountNumberWithLock("1000000001"))
            .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumberWithLock("2000000002"))
            .thenReturn(Optional.of(toAccount));
        when(transferEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransferCommand command = TransferCommand.builder()
            .idempotencyKey("test-key-001")
            .fromAccountNumber("1000000001")
            .toAccountNumber("2000000002")
            .amount(new BigDecimal("100000"))
            .build();

        TransferResult result = transferService.transfer(command);

        assertThat(fromAccount.getBalance()).isEqualByComparingTo("400000");
        assertThat(toAccount.getBalance()).isEqualByComparingTo("200000");
        assertThat(result.getFromBalanceAfter()).isEqualByComparingTo("400000");
        verify(kafkaTemplate, times(1)).send(any(), any(), any());
    }

    @Test
    @DisplayName("잔액 부족 - IllegalStateException 발생")
    void transfer_insufficient_balance() {
        when(accountRepository.findByAccountNumberWithLock("1000000001"))
            .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumberWithLock("2000000002"))
            .thenReturn(Optional.of(toAccount));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransferCommand command = TransferCommand.builder()
            .idempotencyKey("test-key-002")
            .fromAccountNumber("1000000001")
            .toAccountNumber("2000000002")
            .amount(new BigDecimal("999999999"))
            .build();

        assertThatThrownBy(() -> transferService.transfer(command))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("잔액 부족");
    }
}
