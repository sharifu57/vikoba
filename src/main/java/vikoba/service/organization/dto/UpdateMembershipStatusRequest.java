package vikoba.service.organization.dto;

import lombok.Getter;
import lombok.Setter;
import vikoba.service.common.enums.MembershipStatus;

@Getter
@Setter
public class UpdateMembershipStatusRequest {
    private MembershipStatus status;
}
