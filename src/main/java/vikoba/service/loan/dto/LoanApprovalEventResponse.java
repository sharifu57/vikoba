package vikoba.service.loan.dto;
import java.time.LocalDateTime;
public record LoanApprovalEventResponse(int stepOrder, String action, Long actorMemberId, String reason, LocalDateTime actedAt) {}
