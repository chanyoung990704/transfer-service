package dev.chan.transferservice.domain.payment;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    READY("결제 준비"),
    IN_PROGRESS("결제 진행 중"),
    DONE("결제 완료"),
    CANCELED("결제 취소"),
    ABORTED("결제 승인 실패"),
    PARTIAL_CANCELED("부분 취소");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }
}
