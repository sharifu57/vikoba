package vikoba.service.contribution.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class SharePurchaseRequestResponse {
    private Long id;
    private Long groupMemberId;
    private String memberName;
    private String membershipNumber;
    private Integer quantity;
    private BigDecimal amount;
    private String paymentMethod;
    private String paymentReference;
    private String proofText;
    private String proofFileName;
    private String proofContentType;
    private boolean hasProofFile;
    private String status;
    private String reviewReason;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
}
