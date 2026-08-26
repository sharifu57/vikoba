package vikoba.service.loan.dto;
import lombok.Builder; import lombok.Getter; import java.math.BigDecimal; import java.time.LocalDate;
@Getter @Builder public class LoanInstallmentResponse { private Long id; private Integer installmentNumber; private LocalDate dueDate; private BigDecimal principalAmount; private BigDecimal interestAmount; private BigDecimal penaltyAmount; private BigDecimal totalAmount; private BigDecimal paidAmount; private BigDecimal balance; private String status; }
