package vikoba.service.organization.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class GroupSettingsRequest {
    private BigDecimal minimumContribution;
    private BigDecimal maximumContribution;
    private BigDecimal sharePrice;
    private Integer maximumSharesPerMember;
    private BigDecimal loanMultiplier;
    private BigDecimal defaultInterestRate;
    private Integer defaultLoanDurationMonths;
    private BigDecimal latePaymentFine;
}