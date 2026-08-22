package vikoba.service.contribution.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.ContributionStatus;
import vikoba.service.organization.entity.GroupMember;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(
        name = "member_contributions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_member_contribution_period",
                        columnNames = {
                                "group_member_id",
                                "contribution_period_id"
                        }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberContribution extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_member_id", nullable = false)
    private GroupMember groupMember;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contribution_period_id", nullable = false)
    private ContributionPeriod contributionPeriod;

    @Column(
            name = "expected_amount",
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal expectedAmount;

    @Column(
            name = "paid_amount",
            precision = 19,
            scale = 2,
            nullable = false
    )
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(
            name = "balance",
            precision = 19,
            scale = 2,
            nullable = false
    )
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ContributionStatus status =
            ContributionStatus.PENDING;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
