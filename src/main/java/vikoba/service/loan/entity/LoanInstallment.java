package vikoba.service.loan.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.InstallmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;


@Entity
@Table(
        name = "loan_installments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_loan_installment_number",
                        columnNames = {"loan_id", "installment_number"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanInstallment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

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
            name = "penalty_amount",
            precision = 19,
            scale = 2,
            nullable = false
    )
    @Builder.Default
    private BigDecimal penaltyAmount = BigDecimal.ZERO;

    @Column(
            name = "total_amount",
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal totalAmount;

    @Column(
            name = "paid_amount",
            precision = 19,
            scale = 2,
            nullable = false
    )
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InstallmentStatus status = InstallmentStatus.PENDING;
}
