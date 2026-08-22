package vikoba.service.loan.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.LoanStatus;
import vikoba.service.organization.entity.GroupMember;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "loans",
        indexes = {
                @Index(name = "idx_loan_number", columnList = "loan_number"),
                @Index(name = "idx_loan_member", columnList = "group_member_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Loan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_member_id", nullable = false)
    private GroupMember groupMember;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_product_id", nullable = false)
    private LoanProduct loanProduct;

    @Column(name = "loan_number", nullable = false, unique = true, length = 100)
    private String loanNumber;

    @Column(
            name = "principal_amount",
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal principalAmount;

    @Column(
            name = "interest_amount",
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal interestAmount;

    @Column(
            name = "total_amount",
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal totalAmount;

    @Column(name = "duration_months", nullable = false)
    private Integer durationMonths;

    @Column(name = "application_date", nullable = false)
    private LocalDate applicationDate;

    @Column(name = "approval_date")
    private LocalDate approvalDate;

    @Column(name = "disbursement_date")
    private LocalDate disbursementDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private LoanStatus status = LoanStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String purpose;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;
}
