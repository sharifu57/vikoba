package vikoba.service.contribution.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.PaymentMethod;
import vikoba.service.common.enums.PaymentStatus;
import vikoba.service.organization.entity.GroupMember;
import vikoba.service.organization.entity.VikobaGroup;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(
        name = "payments",
        indexes = {
                @Index(name = "idx_payment_reference", columnList = "reference"),
                @Index(name = "idx_payment_date", columnList = "payment_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private VikobaGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_member_id")
    private GroupMember groupMember;

    @Column(nullable = false, unique = true, length = 100)
    private String reference;

    @Column(name = "external_reference", length = 150)
    private String externalReference;

    @Column(
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "payment_date", nullable = false)
    private LocalDateTime paymentDate;

    @Column(length = 255)
    private String description;
}
