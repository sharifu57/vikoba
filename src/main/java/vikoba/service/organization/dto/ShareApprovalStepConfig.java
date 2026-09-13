package vikoba.service.organization.dto;

import vikoba.service.common.enums.GroupRole;

public record ShareApprovalStepConfig(GroupRole role, String label) {}
