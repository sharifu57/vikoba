package vikoba.service.loan.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.GuarantorStatus;
import vikoba.service.organization.entity.GroupMember;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(
        name = "loan_guarantors",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_loan_guarantor",
                        columnNames = {"loan_id", "group_member_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanGuarantor extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_member_id", nullable = false)
    private GroupMember groupMember;

    @Column(
            name = "guaranteed_amount",
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal guaranteedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private GuarantorStatus status = GuarantorStatus.PENDING;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
}
