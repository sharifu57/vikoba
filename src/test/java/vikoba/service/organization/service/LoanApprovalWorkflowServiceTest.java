package vikoba.service.organization.service;

import org.junit.jupiter.api.Test;
import vikoba.service.common.enums.GroupRole;
import vikoba.service.organization.dto.ShareApprovalStepConfig;
import vikoba.service.organization.entity.VikobaGroup;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class LoanApprovalWorkflowServiceTest {
    private final LoanApprovalWorkflowService workflow = new LoanApprovalWorkflowService(null);

    @Test
    void chairMustBeFirst() {
        assertThrows(IllegalArgumentException.class, () -> workflow.configure(new VikobaGroup(), List.of(
                new ShareApprovalStepConfig(GroupRole.ACCOUNTANT, "Accounting"),
                new ShareApprovalStepConfig(GroupRole.GROUP_CHAIRMAN, "Chair"))));
    }

    @Test
    void accountantMustBeLast() {
        assertThrows(IllegalArgumentException.class, () -> workflow.configure(new VikobaGroup(), List.of(
                new ShareApprovalStepConfig(GroupRole.GROUP_CHAIRMAN, "Chair"),
                new ShareApprovalStepConfig(GroupRole.TREASURER, "Treasurer"))));
    }
}
