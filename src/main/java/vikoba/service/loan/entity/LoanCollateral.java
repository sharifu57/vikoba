package vikoba.service.loan.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.CollateralStatus;
import vikoba.service.common.enums.CollateralType;

import java.math.BigDecimal;


@Entity
@Table(name = "loan_collaterals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanCollateral extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CollateralType type;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(
            name = "estimated_value",
            precision = 19,
            scale = 2
    )
    private BigDecimal estimatedValue;

    @Column(name = "document_reference", length = 150)
    private String documentReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CollateralStatus status = CollateralStatus.ACTIVE;
}
