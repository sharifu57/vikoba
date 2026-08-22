package vikoba.service.contribution.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.ShareTransactionType;
import vikoba.service.organization.entity.GroupMember;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "share_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_member_id", nullable = false)
    private GroupMember groupMember;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "share_product_id", nullable = false)
    private ShareProduct shareProduct;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShareTransactionType type;

    @Column(nullable = false)
    private Integer quantity;

    @Column(
            name = "unit_price",
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal unitPrice;

    @Column(
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal totalAmount;

    @Column(nullable = false, unique = true, length = 100)
    private String reference;

    @Column(nullable = false)
    private LocalDateTime transactionDate;
}
