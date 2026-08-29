package vikoba.service.contribution.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShareRedemptionRequest {
    private Long groupMemberId;
    private Integer quantity;
    private String reference;
}
