package dev.chan.transferservice.api.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentCancelRequest {

    @NotBlank(message = "paymentKey는 필수입니다.")
    private String paymentKey;

    @NotBlank(message = "취소 사유는 필수입니다.")
    private String cancelReason;

    @NotNull(message = "취소 금액은 필수입니다.")
    @DecimalMin(value = "100", message = "최소 취소 금액은 100원입니다.")
    private BigDecimal cancelAmount;
}
