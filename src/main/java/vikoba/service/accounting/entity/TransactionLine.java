package vikoba.service.accounting.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;

import java.math.BigDecimal;


@Entity
@Table(name = "transaction_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionLine extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private FinancialTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(
            precision = 19,
            scale = 2,
            nullable = false
    )
    @Builder.Default
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(
            precision = 19,
            scale = 2,
            nullable = false
    )
    @Builder.Default
    private BigDecimal credit = BigDecimal.ZERO;

    @Column(length = 255)
    private String description;
}
