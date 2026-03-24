package dev.chan.transferservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 스케줄링 활성화 (Outbox Relay 용)
public class TransferServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransferServiceApplication.class, args);
    }

}
