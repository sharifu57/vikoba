package vikoba.service.social.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class SocialFundRequestResponse {
    private Long id;
    private Long groupMemberId;
    private String memberName;
    private String membershipNumber;
    private Long fundTypeId;
    private String fundTypeName;
    private String reference;
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    private String reason;
    private String status;
    private LocalDate requestedDate;
    private LocalDate approvedDate;
    private java.util.List<vikoba.service.contribution.dto.ShareApprovalStep> approvalSteps;
    private String currentStepRole;
    private String currentStepLabel;
    private boolean canApprove;
    private boolean canReject;
    private boolean canDisburse;
}
