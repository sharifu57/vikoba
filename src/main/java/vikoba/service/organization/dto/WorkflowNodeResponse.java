package vikoba.service.organization.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WorkflowNodeResponse {
    private Long id;
    private Long groupId;
    private String actionKey;
    private String label;
    private String requiredRole;
    private String requiredPermission;
    private Integer stepOrder;
    private boolean active;
}
