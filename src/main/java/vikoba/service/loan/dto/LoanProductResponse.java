package vikoba.service.loan.dto;
import lombok.Builder; import lombok.Getter; import java.math.BigDecimal;
@Getter @Builder public class LoanProductResponse { private Long id; private String code; private String name; private String description; private BigDecimal minimumAmount; private BigDecimal maximumAmount; private BigDecimal interestRate; private String interestType; private Integer maxDurationMonths; private boolean active; }
