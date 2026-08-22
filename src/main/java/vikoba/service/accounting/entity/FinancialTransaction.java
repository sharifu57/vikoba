package vikoba.service.accounting.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.NoArgsConstructor;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.FinancialTransactionStatus;
import vikoba.service.organization.entity.VikobaGroup;

import java.time.LocalDateTime;


@Entity
@Table(
        name = "financial_transactions",
        indexes = {
                @Index(
                        name = "idx_financial_transaction_date",
                        columnList = "transaction_date"
                ),
                @Index(
                        name = "idx_financial_transaction_reference",
                        columnList = "reference"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialTransaction extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private VikobaGroup group;

    @Column(nullable = false, unique = true, length = 100)
    private String reference;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FinancialTransactionStatus status =
            FinancialTransactionStatus.POSTED;
}
