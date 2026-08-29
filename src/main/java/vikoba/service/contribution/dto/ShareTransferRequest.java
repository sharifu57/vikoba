package vikoba.service.contribution.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShareTransferRequest {
    private Long fromGroupMemberId;
    private Long toGroupMemberId;
    private Integer quantity;
    private String reference;
}
