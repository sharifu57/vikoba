package vikoba.service.organization.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vikoba.service.common.enums.GroupRole;
import vikoba.service.organization.dto.ShareApprovalStepConfig;
import vikoba.service.organization.entity.VikobaGroup;
import vikoba.service.organization.entity.WorkflowNode;
import vikoba.service.organization.repository.WorkflowNodeRepository;
import java.util.List;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class ShareApprovalWorkflowService {
    public static final String ACTION = "SHARE_PURCHASE_APPROVAL";
    private final WorkflowNodeRepository repository;

    public List<ShareApprovalStepConfig> get(Long groupId) {
        var nodes = repository.findByGroupIdAndActionKeyAndActiveTrueOrderByStepOrderAsc(groupId, ACTION);
        if (nodes.isEmpty()) return defaults();
        return nodes.stream().map(n -> new ShareApprovalStepConfig(n.getRequiredRole(), n.getLabel())).toList();
    }

    public List<ShareApprovalStepConfig> defaults() {
        return List.of(new ShareApprovalStepConfig(GroupRole.ACCOUNTANT, "Accountant review"),
                new ShareApprovalStepConfig(GroupRole.GROUP_CHAIRMAN, "Chair approval"));
    }

    public void configure(VikobaGroup group, List<ShareApprovalStepConfig> steps) {
        if (steps == null) steps = defaults();
        if (steps.isEmpty() || steps.size() > 10) throw new IllegalArgumentException("Choose between 1 and 10 approval steps");
        var roles = new HashSet<GroupRole>();
        for (var step : steps) {
            if (step == null || step.role() == null || step.role() == GroupRole.MEMBER)
                throw new IllegalArgumentException("Each approval step needs a reviewer role");
            if (!roles.add(step.role())) throw new IllegalArgumentException("A reviewer role can appear only once");
        }
        var old = repository.findByGroupIdAndActionKeyOrderByStepOrderAsc(group.getId(), ACTION);
        old.forEach(n -> n.setActive(false));
        repository.saveAllAndFlush(old);
        // Reuse existing rows because the group/action/order tuple is unique even for inactive rows.
        for (int i = 0; i < steps.size(); i++) {
            var step = steps.get(i);
            WorkflowNode node = i < old.size() ? old.get(i) : WorkflowNode.builder().group(group).actionKey(ACTION).build();
            node.setStepOrder(i + 1);
            node.setRequiredRole(step.role());
            node.setRequiredPermission(null);
            node.setLabel(step.label() == null || step.label().isBlank() ? step.role().name().replace('_', ' ') + " approval" : step.label().trim());
            node.setActive(true);
            repository.save(node);
        }
    }
}
