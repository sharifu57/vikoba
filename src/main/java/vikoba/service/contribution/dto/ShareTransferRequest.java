package vikoba.service.contribution.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShareTransferRequest {
    private Long fromGroupMemberId;
    private Long toGroupMemberId;
    private java.math.BigDecimal quantity;
    private String reference;
}
