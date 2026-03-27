package dev.chan.transferservice.api.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCreateRequest {

    @NotBlank(message = "주문 번호는 필수입니다.")
    private String orderId;

    @NotNull(message = "결제 금액은 필수입니다.")
    @DecimalMin(value = "100", message = "최소 결제 금액은 100원입니다.")
    private BigDecimal amount;
}
