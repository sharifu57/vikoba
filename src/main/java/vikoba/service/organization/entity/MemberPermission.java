package vikoba.service.organization.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.auth.entity.Permission;
import vikoba.service.common.entity.BaseEntity;

@Entity
@Table(name = "member_permissions", uniqueConstraints = @UniqueConstraint(name = "uk_member_permission", columnNames = { "group_member_id", "permission_id" }))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MemberPermission extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_member_id", nullable = false)
    private GroupMember groupMember;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;
}
