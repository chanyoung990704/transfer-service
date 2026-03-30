package dev.chan.transferservice.config;

import dev.chan.transferservice.domain.payment.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.Optional;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class SettlementJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final PaymentEventRepository paymentEventRepository;
    private final SettlementResultRepository settlementResultRepository;

    @Bean
    public Job settlementJob() {
        return new JobBuilder("settlementJob", jobRepository)
                .start(settlementStep())
                .build();
    }

    @Bean
    public Step settlementStep() {
        return new StepBuilder("settlementStep", jobRepository)
                .<Payment, SettlementResult>chunk(10, transactionManager)
                .reader(paymentReader())
                .processor(paymentProcessor())
                .writer(settlementWriter())
                .build();
    }

    @Bean
    public JpaPagingItemReader<Payment> paymentReader() {
        return new JpaPagingItemReaderBuilder<Payment>()
                .name("paymentReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT p FROM Payment p WHERE p.status = dev.chan.transferservice.domain.payment.PaymentStatus.DONE")
                .pageSize(10)
                .build();
    }

    @Bean
    public ItemProcessor<Payment, SettlementResult> paymentProcessor() {
        return payment -> {
            Optional<PaymentEventEntity> event = paymentEventRepository.findByOrderId(payment.getOrderId());
            
            if (event.isEmpty()) {
                return SettlementResult.builder()
                        .orderId(payment.getOrderId())
                        .paymentAmount(payment.getAmount())
                        .status("MISSING_EVENT")
                        .build();
            }

            BigDecimal eventAmount = event.get().getAmount();
            if (payment.getAmount().compareTo(eventAmount) == 0) {
                return SettlementResult.match(payment.getOrderId(), payment.getAmount());
            } else {
                return SettlementResult.mismatch(payment.getOrderId(), payment.getAmount(), eventAmount);
            }
        };
    }

    @Bean
    public ItemWriter<SettlementResult> settlementWriter() {
        return items -> {
            for (SettlementResult item : items) {
                settlementResultRepository.save(item);
            }
        };
    }
}
