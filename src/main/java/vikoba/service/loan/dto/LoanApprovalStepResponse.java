package vikoba.service.loan.dto;
import java.time.LocalDateTime;
public record LoanApprovalStepResponse(int stepOrder, String role, String label, LocalDateTime approvedAt, Long approvedByMemberId) {}
