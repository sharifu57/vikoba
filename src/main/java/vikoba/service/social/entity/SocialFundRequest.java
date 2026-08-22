package vikoba.service.social.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.SocialFundRequestStatus;
import vikoba.service.organization.entity.GroupMember;

import java.math.BigDecimal;
import java.time.LocalDate;


@Entity
@Table(name = "social_fund_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialFundRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_member_id", nullable = false)
    private GroupMember groupMember;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fund_type_id", nullable = false)
    private SocialFundType fundType;

    @Column(nullable = false, unique = true, length = 100)
    private String reference;

    @Column(
            name = "requested_amount",
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal requestedAmount;

    @Column(
            name = "approved_amount",
            precision = 19,
            scale = 2
    )
    private BigDecimal approvedAmount;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SocialFundRequestStatus status =
            SocialFundRequestStatus.PENDING;

    @Column(name = "requested_date", nullable = false)
    private LocalDate requestedDate;

    @Column(name = "approved_date")
    private LocalDate approvedDate;
}
