package vikoba.service.common.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AuditLogResponse {
    private Long id;
    private Long groupId;
    private String groupName;
    private Long userId;
    private String username;
    private String action;
    private String entityType;
    private Long entityId;
    private String ipAddress;
    private String description;
    private String oldValues;
    private String newValues;
    private LocalDateTime createdAt;
}
