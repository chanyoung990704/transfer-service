package dev.chan.transferservice;

import dev.chan.transferservice.domain.account.Account;
import dev.chan.transferservice.domain.account.AccountRepository;
import dev.chan.transferservice.domain.event.TransferEventEntity;
import dev.chan.transferservice.domain.event.TransferEventRepository;
import dev.chan.transferservice.domain.transfer.TransferCommand;
import dev.chan.transferservice.domain.transfer.TransferResult;
import dev.chan.transferservice.domain.transfer.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class SagaIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferEventRepository transferEventRepository;

    private String fromAcc = "1111111111";
    private String toAcc = "2222222222";

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        transferEventRepository.deleteAll();

        accountRepository.save(new Account(fromAcc, "Sender", new BigDecimal("10000.00")));
        accountRepository.save(new Account(toAcc, "Receiver", new BigDecimal("5000.00")));
    }

    @Test
    @DisplayName("Saga 패턴 통합 검증: 출금(1단계) -> 입금(2단계) 성공 케이스")
    void saga_full_flow_success() {
        // 1. Saga 1단계 실행 (출금)
        TransferCommand command = TransferCommand.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .fromAccountNumber(fromAcc)
                .toAccountNumber(toAcc)
                .amount(new BigDecimal("1000.00"))
                .build();

        TransferResult result = transferService.transfer(command);

        // 검증: 출금 계좌 잔액 차감 및 이벤트 상태 WITHDRAWN 확인
        Account sender = accountRepository.findByAccountNumber(fromAcc).get();
        assertThat(sender.getBalance()).isEqualByComparingTo("9000.00");

        TransferEventEntity event = transferEventRepository.findById(result.getEventId()).get();
        assertThat(event.getStatus()).isEqualTo(TransferEventEntity.STATUS_WITHDRAWN);

        // 2. Saga 2단계 실행 (입금 - Consumer가 호출하는 상황 시뮬레이션)
        transferService.deposit(event.getEventId(), toAcc, new BigDecimal("1000.00"));

        // 최종 검증: 입금 계좌 잔액 증가 및 이벤트 상태 COMPLETED 확인
        Account receiver = accountRepository.findByAccountNumber(toAcc).get();
        assertThat(receiver.getBalance()).isEqualByComparingTo("6000.00");

        TransferEventEntity finalEvent = transferEventRepository.findById(result.getEventId()).get();
        assertThat(finalEvent.getStatus()).isEqualTo(TransferEventEntity.STATUS_COMPLETED);
    }
}
