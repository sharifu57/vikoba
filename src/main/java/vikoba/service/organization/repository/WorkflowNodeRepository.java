package vikoba.service.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.organization.entity.WorkflowNode;

import java.util.List;

public interface WorkflowNodeRepository extends JpaRepository<WorkflowNode, Long> {
    List<WorkflowNode> findByGroupIdAndActiveTrueOrderByActionKeyAscStepOrderAsc(Long groupId);

    List<WorkflowNode> findByGroupIdAndActionKeyAndActiveTrueOrderByStepOrderAsc(Long groupId, String actionKey);
}
