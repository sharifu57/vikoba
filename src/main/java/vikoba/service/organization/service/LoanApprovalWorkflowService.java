package vikoba.service.organization.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vikoba.service.common.enums.GroupRole;
import vikoba.service.organization.dto.ShareApprovalStepConfig;
import vikoba.service.organization.entity.VikobaGroup;
import vikoba.service.organization.entity.WorkflowNode;
import vikoba.service.organization.repository.WorkflowNodeRepository;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LoanApprovalWorkflowService {
    public static final String ACTION = "LOAN_APPROVAL";
    private final WorkflowNodeRepository repository;

    public List<ShareApprovalStepConfig> get(Long groupId) {
        var nodes = repository.findByGroupIdAndActionKeyAndActiveTrueOrderByStepOrderAsc(groupId, ACTION);
        if (nodes.isEmpty()) return List.of(
                new ShareApprovalStepConfig(GroupRole.GROUP_CHAIRMAN, "Chair review"),
                new ShareApprovalStepConfig(GroupRole.ACCOUNTANT, "Accountant and disbursement"));
        return nodes.stream().map(node -> new ShareApprovalStepConfig(node.getRequiredRole(), node.getLabel())).toList();
    }

    public void configure(VikobaGroup group, List<ShareApprovalStepConfig> steps) {
        if (steps == null || steps.isEmpty() || steps.size() > 10)
            throw new IllegalArgumentException("Choose between 1 and 10 loan approval steps.");
        var roles = new HashSet<GroupRole>();
        for (var step : steps) {
            if (step == null || step.role() == null || step.role() == GroupRole.MEMBER || !roles.add(step.role()))
                throw new IllegalArgumentException("Each loan reviewer must have a distinct group role.");
        }
        var old = repository.findByGroupIdAndActionKeyOrderByStepOrderAsc(group.getId(), ACTION);
        old.forEach(node -> node.setActive(false));
        repository.saveAllAndFlush(old);
        for (int i = 0; i < steps.size(); i++) {
            var step = steps.get(i);
            var node = i < old.size() ? old.get(i) : WorkflowNode.builder().group(group).actionKey(ACTION).build();
            node.setStepOrder(i + 1);
            node.setRequiredRole(step.role());
            node.setRequiredPermission("LOAN_MANAGE");
            node.setLabel(step.label() == null || step.label().isBlank() ? step.role().name().replace('_', ' ') + " approval" : step.label().trim());
            node.setActive(true);
            repository.save(node);
        }
    }
}
