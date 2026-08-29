package vikoba.service.loan.dto;
import lombok.Getter; import lombok.Setter; import java.math.BigDecimal; import java.util.List;
@Getter @Setter public class LoanRequest { private Long groupMemberId; private Long loanProductId; private BigDecimal principalAmount; private Integer durationMonths; private String purpose; private List<Long> guarantorIds; }
