package vikoba.service.organization.dto;

import lombok.Getter;
import lombok.Setter;
import vikoba.service.common.enums.GroupRole;
import java.util.List;

@Getter @Setter
public class MemberAccessRequest {
    private List<GroupRole> roles;
    private List<String> permissions;
}
