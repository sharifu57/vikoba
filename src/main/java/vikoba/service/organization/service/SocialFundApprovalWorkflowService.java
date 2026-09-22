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
public class SocialFundApprovalWorkflowService {
    public static final String ACTION = "SOCIAL_FUND_APPROVAL";
    private final WorkflowNodeRepository repository;

    public List<ShareApprovalStepConfig> get(Long groupId) {
        var nodes = repository.findByGroupIdAndActionKeyAndActiveTrueOrderByStepOrderAsc(groupId, ACTION);
        if (nodes.isEmpty()) return List.of(
                new ShareApprovalStepConfig(GroupRole.ACCOUNTANT, "Accountant review"),
                new ShareApprovalStepConfig(GroupRole.GROUP_CHAIRMAN, "Chair approval"));
        return nodes.stream().map(node -> new ShareApprovalStepConfig(node.getRequiredRole(), node.getLabel())).toList();
    }

    public void configure(VikobaGroup group, List<ShareApprovalStepConfig> steps) {
        if (steps == null || steps.isEmpty() || steps.size() > 10)
            throw new IllegalArgumentException("Choose between 1 and 10 Jamii approval steps.");
        var roles = new HashSet<GroupRole>();
        for (var step : steps) {
            if (step == null || step.role() == null || step.role() == GroupRole.MEMBER || !roles.add(step.role()))
                throw new IllegalArgumentException("Use a different reviewer role for every Jamii approval step.");
        }
        var old = repository.findByGroupIdAndActionKeyOrderByStepOrderAsc(group.getId(), ACTION);
        old.forEach(node -> node.setActive(false));
        repository.saveAllAndFlush(old);
        for (int index = 0; index < steps.size(); index++) {
            var config = steps.get(index);
            WorkflowNode node = index < old.size() ? old.get(index)
                    : WorkflowNode.builder().group(group).actionKey(ACTION).build();
            node.setStepOrder(index + 1);
            node.setRequiredRole(config.role());
            node.setRequiredPermission(null);
            node.setLabel(config.label() == null || config.label().isBlank()
                    ? config.role().name().replace('_', ' ') + " approval" : config.label().trim());
            node.setActive(true);
            repository.save(node);
        }
    }
}
