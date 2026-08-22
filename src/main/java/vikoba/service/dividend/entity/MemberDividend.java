package vikoba.service.dividend.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.DividendStatus;
import vikoba.service.organization.entity.GroupMember;

import java.math.BigDecimal;

@Entity
@Table(
        name = "member_dividends",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_distribution_member",
                        columnNames = {
                                "distribution_id",
                                "group_member_id"
                        }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberDividend extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "distribution_id", nullable = false)
    private ProfitDistribution distribution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_member_id", nullable = false)
    private GroupMember groupMember;

    @Column(
            name = "share_count",
            nullable = false
    )
    private Integer shareCount;

    @Column(
            name = "dividend_amount",
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal dividendAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DividendStatus status;
}
