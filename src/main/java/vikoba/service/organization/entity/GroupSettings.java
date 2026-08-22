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

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "group_id",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(name = "fk_group_settings_group")
    )
    private VikobaGroup group;

    @Column(
            name = "minimum_contribution",
            precision = 19,
            scale = 2
    )
    @Builder.Default
    private BigDecimal minimumContribution = BigDecimal.ZERO;

    @Column(
            name = "maximum_contribution",
            precision = 19,
            scale = 2
    )
    private BigDecimal maximumContribution;

    @Column(
            name = "share_price",
            precision = 19,
            scale = 2
    )
    @Builder.Default
    private BigDecimal sharePrice = BigDecimal.ZERO;

    @Column(name = "maximum_shares_per_member")
    private Integer maximumSharesPerMember;

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
}
