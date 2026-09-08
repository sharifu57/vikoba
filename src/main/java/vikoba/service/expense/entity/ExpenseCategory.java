package vikoba.service.expense.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.organization.entity.VikobaGroup;


@Entity
@Table(name = "expense_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseCategory extends BaseEntity {
    /** Categories are shared reference data, not owned by an individual Kikoba group. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private VikobaGroup group;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
