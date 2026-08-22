package vikoba.service.expense.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.ExpenseStatus;
import vikoba.service.organization.entity.VikobaGroup;

import java.math.BigDecimal;
import java.time.LocalDate;


@Entity
@Table(name = "expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private VikobaGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ExpenseCategory category;

    @Column(nullable = false, unique = true, length = 100)
    private String reference;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate expenseDate;

    @Column(name = "receipt_number", length = 100)
    private String receiptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ExpenseStatus status = ExpenseStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;
}
