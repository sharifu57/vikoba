package vikoba.service.expense.dto;

public record ExpenseApprovalStep(String role, String label, String approvedAt, Long approvedBy) {}
