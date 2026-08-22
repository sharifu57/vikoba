package vikoba.service.loan.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.NoArgsConstructor;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.contribution.entity.Payment;

import java.math.BigDecimal;


@Entity
@Table(name = "loan_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanPayment extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "installment_id")
    private LoanInstallment installment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal principalAmount;

    @Column(
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal interestAmount;

    @Column(
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal penaltyAmount;

    @Column(
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal totalAmount;
}
