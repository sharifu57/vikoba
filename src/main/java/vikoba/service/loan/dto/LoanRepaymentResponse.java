package vikoba.service.loan.dto;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class LoanRepaymentResponse {
    private Long id;
    private Long loanId;
    private String loanNumber;
    private Long groupMemberId;
    private String memberName;
    private BigDecimal amount;
    private String paymentMethod;
    private String externalReference;
    private String status;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private String rejectionReason;
    private boolean canApprove;
}
