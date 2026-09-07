package vikoba.service.organization.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;

import java.math.BigDecimal;


@Entity
@Table(name = "group_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupSettings extends BaseEntity {

    /** Backward-compatible accessors for existing API consumers. New clients use minimumSharePurchaseAmount. */
    @Deprecated public BigDecimal getMinimumContribution() { return minimumSharePurchaseAmount; }
    @Deprecated public void setMinimumContribution(BigDecimal value) { minimumSharePurchaseAmount = value; }
    @Deprecated public BigDecimal getMaximumContribution() { return null; }
    @Deprecated public void setMaximumContribution(BigDecimal ignored) { }
    @Deprecated public Integer getMaximumSharesPerMember() { return null; }
    @Deprecated public void setMaximumSharesPerMember(Integer ignored) { }

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "group_id",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(name = "fk_group_settings_group")
    )
    private VikobaGroup group;

    @Column(
            name = "minimum_share_purchase_amount",
            precision = 19,
            scale = 2
    )
    @Builder.Default
    private BigDecimal minimumSharePurchaseAmount = BigDecimal.ZERO;

    @Column(
            name = "share_price",
            precision = 19,
            scale = 2
    )
    @Builder.Default
    private BigDecimal sharePrice = BigDecimal.ZERO;

    @Column(name = "required_loan_guarantors")
    private Integer requiredLoanGuarantors;

    @Column(
            name = "loan_multiplier",
            precision = 10,
            scale = 2
    )
    private BigDecimal loanMultiplier;

    @Column(
            name = "default_interest_rate",
            precision = 10,
            scale = 4
    )
    private BigDecimal defaultInterestRate;

    @Column(name = "default_loan_duration_months")
    private Integer defaultLoanDurationMonths;

    @Column(
            name = "late_payment_fine",
            precision = 19,
            scale = 2
    )
    private BigDecimal latePaymentFine;

    @Column(name = "jamii_contribution_per_share_payment", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal jamiiContributionPerSharePayment = BigDecimal.ZERO;
}
