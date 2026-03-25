package dev.chan.transferservice.domain.transfer;

import dev.chan.transferservice.domain.account.*;
import dev.chan.transferservice.domain.event.*;
import dev.chan.transferservice.audit.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock TransferEventRepository transferEventRepository;
    @Mock AuditLogRepository auditLogRepository;

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
    @DisplayName("Saga 1단계(출금) 성공 - 출금 완료 및 WITHDRAWN 이벤트 저장")
    void transfer_step1_success() {
        // given
        when(accountRepository.findByAccountNumberWithLock("1000000001"))
            .thenReturn(Optional.of(fromAccount));
        
        ArgumentCaptor<TransferEventEntity> eventCaptor = ArgumentCaptor.forClass(TransferEventEntity.class);

        TransferCommand command = TransferCommand.builder()
            .idempotencyKey("saga-test-001")
            .fromAccountNumber("1000000001")
            .toAccountNumber("2000000002")
            .amount(new BigDecimal("100000"))
            .build();

        // when
        TransferResult result = transferService.transfer(command);

        // then
        assertThat(fromAccount.getBalance()).isEqualByComparingTo("400000"); // 출금 완료
        verify(transferEventRepository, times(1)).save(eventCaptor.capture());
        
        TransferEventEntity captured = eventCaptor.getValue();
        assertThat(captured.getStatus()).isEqualTo(TransferEventEntity.STATUS_WITHDRAWN);
        assertThat(captured.isPublished()).isFalse();
    }

    @Test
    @DisplayName("Saga 2단계(입금) 성공 - 입금 완료 및 COMPLETED 상태 업데이트")
    void transfer_step2_success() {
        // given
        String eventId = "test-event-id";
        TransferEventEntity eventEntity = spy(TransferEventEntity.builder()
            .eventId(eventId)
            .fromAccountNumber("1000000001")
            .toAccountNumber("2000000002")
            .amount(new BigDecimal("100000"))
            .status(TransferEventEntity.STATUS_WITHDRAWN)
            .build());

        when(transferEventRepository.findById(eventId)).thenReturn(Optional.of(eventEntity));
        when(accountRepository.findByAccountNumberWithLock("2000000002")).thenReturn(Optional.of(toAccount));

        // when
        transferService.deposit(eventId, "2000000002", new BigDecimal("100000"));

        // then
        assertThat(toAccount.getBalance()).isEqualByComparingTo("200000"); // 입금 완료
        assertThat(eventEntity.getStatus()).isEqualTo(TransferEventEntity.STATUS_COMPLETED);
    }

    @Test
    @DisplayName("Saga 보상 트랜잭션(환불) 성공 - 출금 계좌 원복 및 REFUNDED 상태 업데이트")
    void saga_compensation_success() {
        // given
        String eventId = "test-event-id";
        TransferEventEntity eventEntity = spy(TransferEventEntity.builder()
            .eventId(eventId)
            .fromAccountNumber("1000000001")
            .amount(new BigDecimal("100000"))
            .status(TransferEventEntity.STATUS_DEPOSIT_FAILED)
            .build());

        when(transferEventRepository.findById(eventId)).thenReturn(Optional.of(eventEntity));
        when(accountRepository.findByAccountNumberWithLock("1000000001")).thenReturn(Optional.of(fromAccount));

        // when
        transferService.refund(eventId);

        // then
        assertThat(fromAccount.getBalance()).isEqualByComparingTo("600000"); // 원래 50만 + 환불 10만
        assertThat(eventEntity.getStatus()).isEqualTo(TransferEventEntity.STATUS_REFUNDED);
    }
}
