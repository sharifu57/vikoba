package vikoba.service.fine.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.FineStatus;
import vikoba.service.organization.entity.GroupMember;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "fines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fine extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_member_id", nullable = false)
    private GroupMember groupMember;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fine_type_id", nullable = false)
    private FineType fineType;

    @Column(nullable = false, unique = true, length = 100)
    private String reference;

    @Column(
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal amount;

    @Column(
            name = "paid_amount",
            precision = 19,
            scale = 2,
            nullable = false
    )
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "issued_date", nullable = false)
    private LocalDate issuedDate;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FineStatus status = FineStatus.UNPAID;
}
