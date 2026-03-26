package dev.chan.transferservice.domain.transfer;

import dev.chan.transferservice.domain.account.*;
import dev.chan.transferservice.domain.event.*;
import dev.chan.transferservice.audit.*;
import dev.chan.transferservice.config.RedisPublisher;
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
    @Mock RedisPublisher redisPublisher;
    @Mock PendingEventRedisService pendingEventRedisService;

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
    @DisplayName("Saga 1단계(출금) 성공 - 출금 완료 및 Redis ZSet 등록 확인")
    void transfer_step1_success() {
        // given
        when(accountRepository.findByAccountNumberWithLock("1000000001"))
            .thenReturn(Optional.of(fromAccount));
        
        TransferCommand command = TransferCommand.builder()
            .idempotencyKey("saga-test-001")
            .fromAccountNumber("1000000001")
            .toAccountNumber("2000000002")
            .amount(new BigDecimal("100000"))
            .build();

        // when
        transferService.transfer(command);

        // then
        assertThat(fromAccount.getBalance()).isEqualByComparingTo("400000");
        verify(pendingEventRedisService, times(1)).addPendingEvent(anyString(), any());
        verify(redisPublisher, times(1)).publish(anyString(), anyString());
    }

    @Test
    @DisplayName("Saga 2단계(입금) 성공 - 입금 완료 및 Redis ZSet 제거 확인")
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
        assertThat(toAccount.getBalance()).isEqualByComparingTo("200000");
        verify(pendingEventRedisService, times(1)).removePendingEvents(anyCollection());
        verify(redisPublisher, times(1)).publish(anyString(), contains("COMPLETED"));
    }
}
