package vikoba.service.contribution.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.PaymentAllocationType;

import java.math.BigDecimal;

@Entity
@Table(name = "payment_allocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentAllocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentAllocationType type;

    @Column(
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal amount;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(length = 255)
    private String description;
}
