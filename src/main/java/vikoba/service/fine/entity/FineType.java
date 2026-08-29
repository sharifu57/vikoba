package vikoba.service.fine.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.organization.entity.VikobaGroup;

import java.math.BigDecimal;

@Entity
@Table(name = "fine_types", uniqueConstraints = {
                @UniqueConstraint(name = "uk_fine_type_group_code", columnNames = { "group_id", "code" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FineType extends BaseEntity {
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "group_id", nullable = false)
        private VikobaGroup group;

        @Column(nullable = false, length = 50)
        private String code;

        @Column(nullable = false, length = 100)
        private String name;

        @Column(name = "default_amount", precision = 19, scale = 2, nullable = false)
        private BigDecimal defaultAmount;

        @Column(length = 255)
        private String description;

        @Column(nullable = false)
        @Builder.Default
        private boolean active = true;
}
