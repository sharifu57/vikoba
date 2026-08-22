package vikoba.service.dividend.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.NoArgsConstructor;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.DistributionStatus;
import vikoba.service.organization.entity.VikobaGroup;

import java.math.BigDecimal;


@Entity
@Table(name = "profit_distributions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfitDistribution extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private VikobaGroup group;

    @Column(nullable = false, length = 100)
    private String reference;

    @Column(name = "financial_year", nullable = false)
    private Integer financialYear;

    @Column(
            name = "total_profit",
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal totalProfit;

    @Column(
            name = "distribution_amount",
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal distributionAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DistributionStatus status;
}
