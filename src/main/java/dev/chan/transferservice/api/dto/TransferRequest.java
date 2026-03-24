package dev.chan.transferservice.api.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {

    @NotBlank(message = "출금 계좌번호는 필수입니다.")
    @Size(min = 10, max = 20)
    private String fromAccountNumber;

    @NotBlank(message = "입금 계좌번호는 필수입니다.")
    @Size(min = 10, max = 20)
    private String toAccountNumber;

    @NotNull(message = "이체 금액은 필수입니다.")
    @DecimalMin(value = "0.01", message = "이체 금액은 0보다 커야 합니다.")
    @DecimalMax(value = "100000000", message = "1회 이체 한도는 1억원입니다.")
    private BigDecimal amount;
}
