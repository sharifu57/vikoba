package vikoba.service.contribution.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ShareOwnershipResponse {
    private Long groupMemberId;
    private String memberName;
    private String membershipNumber;
    private Integer sharesOwned;
    private BigDecimal unitPrice;
    private BigDecimal equityValue;
    private double ownershipPercentage;
}
