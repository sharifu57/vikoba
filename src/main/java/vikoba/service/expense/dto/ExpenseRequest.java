package vikoba.service.expense.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class ExpenseRequest {
    private Long categoryId;
    private String categoryName;
    private String reference;
    private String description;
    private BigDecimal amount;
    private LocalDate expenseDate;
    private String receiptNumber;
    private String status;
    private String rejectionReason;
}
