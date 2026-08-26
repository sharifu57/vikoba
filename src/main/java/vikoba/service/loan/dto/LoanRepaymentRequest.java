package vikoba.service.loan.dto;
import lombok.Getter; import lombok.Setter; import java.math.BigDecimal;
@Getter @Setter public class LoanRepaymentRequest { private BigDecimal amount; private String paymentMethod; private String externalReference; }
