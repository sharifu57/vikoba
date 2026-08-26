package vikoba.service.loan.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.InterestType;
import vikoba.service.organization.entity.VikobaGroup;

import java.math.BigDecimal;


@Entity
@Table(
        name = "loan_products",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_loan_product_group_code",
                        columnNames = {"group_id", "code"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanProduct extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = true)
    private VikobaGroup group;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(
            name = "minimum_amount",
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal minimumAmount;

    @Column(
            name = "maximum_amount",
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal maximumAmount;

    @Column(
            name = "interest_rate",
            precision = 10,
            scale = 4,
            nullable = false
    )
    private BigDecimal interestRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_type", nullable = false, length = 20)
    private InterestType interestType;

    @Column(name = "max_duration_months", nullable = false)
    private Integer maxDurationMonths;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
