package vikoba.service.contribution.entity;

import jakarta.persistence.*;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;
import lombok.NoArgsConstructor;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.ContributionPeriodStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "contribution_periods",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_contribution_period",
                        columnNames = {
                                "contribution_type_id",
                                "period_start",
                                "period_end"
                        }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContributionPeriod extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contribution_type_id", nullable = false)
    private ContributionType contributionType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(
            name = "expected_amount",
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal expectedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ContributionPeriodStatus status =
            ContributionPeriodStatus.OPEN;
}
