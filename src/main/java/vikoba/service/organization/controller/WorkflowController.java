package vikoba.service.organization.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.organization.dto.WorkflowNodeRequest;
import vikoba.service.organization.dto.WorkflowNodeResponse;
import vikoba.service.organization.entity.WorkflowNode;
import vikoba.service.organization.repository.VikobaGroupRepository;
import vikoba.service.organization.repository.WorkflowNodeRepository;
import vikoba.service.organization.service.GroupAuthorizationService;
import vikoba.service.auth.repository.PermissionRepository;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workflows")
public class WorkflowController {
    private final WorkflowNodeRepository repository;
    private final VikobaGroupRepository groupRepository;
    private final GroupAuthorizationService authorizationService;
    private final PermissionRepository permissionRepository;

    @GetMapping("/group/{groupId}")
    public ResponseEntity<ApiResponse<List<WorkflowNodeResponse>>> list(@PathVariable Long groupId) {
        authorizationService.requireMembership(groupId);
        return ResponseEntity.ok(ApiResponse.success("Workflow nodes retrieved successfully.",
                repository.findByGroupIdAndActiveTrueOrderByActionKeyAscStepOrderAsc(groupId).stream()
                        .map(this::map).toList()));
    }

    @PostMapping("/group/{groupId}/nodes")
    public ResponseEntity<ApiResponse<WorkflowNodeResponse>> create(@PathVariable Long groupId,
            @RequestBody WorkflowNodeRequest request) {
        authorizationService.requirePermission(groupId, "WORKFLOW_MANAGE");
        if (request.getActionKey() == null || request.getActionKey().isBlank()
                || request.getRequiredPermission() == null || request.getRequiredPermission().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("actionKey and requiredPermission are required"));
        }
        String permission = request.getRequiredPermission().trim().toUpperCase();
        if (!permissionRepository.existsByName(permission)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("The required permission is not configured"));
        }
        WorkflowNode node = WorkflowNode.builder()
                .group(groupRepository.findById(groupId)
                        .orElseThrow(() -> new IllegalArgumentException("Group not found")))
                .actionKey(request.getActionKey().trim().toUpperCase())
                .label(request.getLabel() == null ? request.getActionKey() : request.getLabel().trim())
                .requiredRole(request.getRequiredRole())
                .requiredPermission(permission)
                .stepOrder(request.getStepOrder() == null ? 1 : request.getStepOrder())
                .active(request.getActive() == null || request.getActive()).build();
        return ResponseEntity
                .ok(ApiResponse.success("Workflow node created successfully.", map(repository.save(node))));
    }

    private WorkflowNodeResponse map(WorkflowNode node) {
        return WorkflowNodeResponse.builder().id(node.getId()).groupId(node.getGroup().getId())
                .actionKey(node.getActionKey()).label(node.getLabel())
                .requiredRole(node.getRequiredRole() == null ? null : node.getRequiredRole().name())
                .requiredPermission(node.getRequiredPermission()).stepOrder(node.getStepOrder()).active(node.isActive())
                .build();
    }
}
