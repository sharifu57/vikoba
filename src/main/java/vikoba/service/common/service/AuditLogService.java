package vikoba.service.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.auth.entity.User;
import vikoba.service.auth.repository.UserRepository;
import vikoba.service.common.dto.AuditLogResponse;
import vikoba.service.common.entity.AuditLog;
import vikoba.service.common.enums.AuditAction;
import vikoba.service.common.repository.AuditLogRepository;
import vikoba.service.organization.entity.VikobaGroup;
import vikoba.service.organization.repository.VikobaGroupRepository;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final AuditLogRepository repository;
    private final UserRepository userRepository;
    private final VikobaGroupRepository groupRepository;

    @Transactional
    public void record(String username, Long groupId, AuditAction action, String entityType, Long entityId,
            String ipAddress, String description) {
        User user = username == null ? null
                : userRepository.findByUsername(username)
                        .orElseGet(() -> userRepository.findByPhone(username).orElse(null));
        VikobaGroup group = groupId == null ? null : groupRepository.findById(groupId).orElse(null);
        repository.save(AuditLog.builder().user(user).group(group).action(action).entityType(entityType)
                .entityId(entityId).ipAddress(ipAddress).description(description).build());
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> list(Long groupId, int page, int size) {
        if (groupId == null || !groupRepository.existsById(groupId)) {
            throw new IllegalArgumentException("Group not found.");
        }
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return repository.findByGroupIdOrderByCreatedAtDesc(groupId, request).map(this::toResponse);
    }

    private AuditLogResponse toResponse(AuditLog item) {
        return AuditLogResponse.builder().id(item.getId())
                .groupId(item.getGroup() == null ? null : item.getGroup().getId())
                .groupName(item.getGroup() == null ? null : item.getGroup().getName())
                .userId(item.getUser() == null ? null : item.getUser().getId())
                .username(item.getUser() == null ? "System"
                        : (item.getUser().getUsername() == null ? item.getUser().getPhone()
                                : item.getUser().getUsername()))
                .action(item.getAction().name()).entityType(item.getEntityType()).entityId(item.getEntityId())
                .ipAddress(item.getIpAddress()).description(item.getDescription()).oldValues(item.getOldValues())
                .newValues(item.getNewValues()).createdAt(item.getCreatedAt()).build();
    }
}
