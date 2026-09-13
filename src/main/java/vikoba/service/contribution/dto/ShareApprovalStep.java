package vikoba.service.contribution.dto;

public record ShareApprovalStep(String role, String label, String approvedAt, Long approvedBy, boolean skipped) {}
