package vikoba.service.fine.dto;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class FineInput {
    private Long groupMemberId;
    private Long fineTypeId;
    private String fineType;
    private BigDecimal amount;
    private String reason;
    private LocalDate issuedDate;
    private BigDecimal paymentAmount;
    private String status;
}
