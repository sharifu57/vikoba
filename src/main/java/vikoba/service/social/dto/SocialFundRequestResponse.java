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
}
