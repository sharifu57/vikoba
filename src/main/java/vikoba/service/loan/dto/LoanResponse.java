package vikoba.service.loan.dto;


import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanResponse {

    private Long id;

    private Long groupMemberId;
    private String memberName;
    private String membershipNumber;
    private Long loanProductId;
    private String loanProductName;
    private BigDecimal interestRate;

    private String loanNumber;

    private BigDecimal principalAmount;

    private BigDecimal interestAmount;

    private BigDecimal totalAmount;

    private Integer durationMonths;

    private LocalDate applicationDate;

    private LocalDate approvalDate;

    private LocalDate disbursementDate;

    private LocalDate maturityDate;

    private String status;

    private String purpose;

    private String rejectionReason;
    private BigDecimal totalPaid;
    private BigDecimal remainingBalance;
    private Integer progress;
}
