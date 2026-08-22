package vikoba.service.social.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.organization.entity.GroupMember;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "social_fund_contributions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialFundContribution extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_member_id", nullable = false)
    private GroupMember groupMember;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fund_type_id", nullable = false)
    private SocialFundType fundType;

    @Column(
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate contributionDate;

    @Column(nullable = false, unique = true, length = 100)
    private String reference;
}
