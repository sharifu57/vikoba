package vikoba.service.organization.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.GroupRole;

@Entity
@Table(name = "workflow_nodes", uniqueConstraints = @UniqueConstraint(name = "uk_workflow_group_action_step", columnNames = {
        "group_id", "action_key", "step_order" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowNode extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private VikobaGroup group;

    @Column(name = "action_key", nullable = false, length = 80)
    private String actionKey;

    @Column(name = "label", nullable = false, length = 150)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_role", length = 50)
    private GroupRole requiredRole;

    @Column(name = "required_permission", length = 100)
    private String requiredPermission;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
