package vikoba.service.contribution.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.organization.entity.VikobaGroup;

import java.math.BigDecimal;


@Entity
@Table(
        name = "share_products",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_share_product_group_code",
                        columnNames = {"group_id", "code"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareProduct extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private VikobaGroup group;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(
            name = "share_price",
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal sharePrice;

    @Column(name = "minimum_shares")
    private Integer minimumShares;

    @Column(name = "maximum_shares")
    private Integer maximumShares;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
