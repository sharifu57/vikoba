package vikoba.service.social.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.organization.entity.VikobaGroup;

import java.math.BigDecimal;


@Entity
@Table(
        name = "social_fund_types",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_social_fund_type_group_code",
                        columnNames = {"group_id", "code"}
                )
        },
        indexes = {
                @Index(name = "idx_social_fund_type_group", columnList = "group_id"),
                @Index(name = "idx_social_fund_type_active", columnList = "active")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialFundType extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "group_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_social_fund_type_group")
    )
    private VikobaGroup group;

    /**
     * Unique code within the group.
     * Example: DEATH, HOSPITAL, WEDDING
     */
    @Column(nullable = false, length = 50)
    private String code;

    /**
     * Display name.
     * Example: Death Support Fund
     */
    @Column(nullable = false, length = 150)
    private String name;

    /**
     * Description of what this fund is intended for.
     */
    @Column(length = 500)
    private String description;

    /**
     * Default amount members contribute.
     */
    @Column(
            name = "default_contribution",
            precision = 19,
            scale = 2
    )
    private BigDecimal defaultContribution;

    /**
     * Whether this fund is mandatory for members.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean mandatory = false;

    /**
     * Whether the fund is currently available.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
