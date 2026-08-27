package vikoba.service.fine.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FineResponse {

    private Long id;

    private Long groupMemberId;

    private String memberName;
    private String membershipNumber;
    private Long fineTypeId;
    private String fineTypeName;
    private String reference;

    private BigDecimal amount;

    private BigDecimal paidAmount;

    private BigDecimal balance;

    private String reason;

    private String status;

    private LocalDate fineDate;

    private LocalDate dueDate;

    private LocalDate paidDate;
}
