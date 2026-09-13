package vikoba.service.expense.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ExpenseResponse {
    private Long id;
    private Long groupId;
    private Long categoryId;
    private String categoryName;
    private String reference;
    private String description;
    private BigDecimal amount;
    private LocalDate expenseDate;
    private String receiptNumber;
    private String status;
    private String rejectionReason;
    private List<ExpenseApprovalStep> approvalSteps;
    private String currentStepLabel;
    private boolean canApprove;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
