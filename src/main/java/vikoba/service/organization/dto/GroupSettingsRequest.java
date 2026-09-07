package vikoba.service.organization.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class GroupSettingsRequest {
    /** Kept only so older mobile/web clients can submit a transition payload. */
    @Deprecated public BigDecimal getMinimumContribution() { return minimumSharePurchaseAmount; }
    @Deprecated public void setMinimumContribution(BigDecimal value) { minimumSharePurchaseAmount = value; }
    @Deprecated public BigDecimal getMaximumContribution() { return null; }
    @Deprecated public void setMaximumContribution(BigDecimal ignored) { }
    @Deprecated public Integer getMaximumSharesPerMember() { return null; }
    @Deprecated public void setMaximumSharesPerMember(Integer ignored) { }
    private BigDecimal minimumSharePurchaseAmount;
    private BigDecimal sharePrice;
    private Integer requiredLoanGuarantors;
    private BigDecimal loanMultiplier;
    private BigDecimal defaultInterestRate;
    private Integer defaultLoanDurationMonths;
    private BigDecimal latePaymentFine;
    private BigDecimal jamiiContributionPerSharePayment;
}
