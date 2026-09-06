package vikoba.service.organization.dto;

import lombok.Getter;
import lombok.Setter;
import vikoba.service.common.enums.GroupRole;

@Getter
@Setter
public class WorkflowNodeRequest {
    private String actionKey;
    private String label;
    private GroupRole requiredRole;
    private String requiredPermission;
    private Integer stepOrder;
    private Boolean active;
}
