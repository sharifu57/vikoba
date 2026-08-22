package vikoba.service.contribution.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.ContributionFrequency;
import vikoba.service.organization.entity.VikobaGroup;

import java.math.BigDecimal;


@Entity
@Table(
        name = "contribution_types",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_contribution_type_group_code",
                        columnNames = {"group_id", "code"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContributionType extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private VikobaGroup group;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(
            name = "default_amount",
            precision = 19,
            scale = 2,
            nullable = false
    )
    private BigDecimal defaultAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ContributionFrequency frequency;

    @Column(nullable = false)
    @Builder.Default
    private boolean mandatory = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
