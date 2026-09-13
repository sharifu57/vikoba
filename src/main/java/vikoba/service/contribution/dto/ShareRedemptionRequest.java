package vikoba.service.contribution.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShareRedemptionRequest {
    private Long groupMemberId;
    private java.math.BigDecimal quantity;
    private String reference;
}
