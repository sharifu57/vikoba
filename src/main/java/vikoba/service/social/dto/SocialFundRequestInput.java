package vikoba.service.social.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SocialFundRequestInput {
    private Long groupMemberId;
    private Long fundTypeId;
    private BigDecimal requestedAmount;
    private String reason;
}
