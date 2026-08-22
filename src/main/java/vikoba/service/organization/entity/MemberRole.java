package vikoba.service.organization.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.GroupRole;

import java.time.LocalDate;

@Entity
@Table(name = "member_roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberRole extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "group_member_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_member_role_group_member")
    )
    private GroupMember groupMember;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private GroupRole role;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
