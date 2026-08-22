package vikoba.service.organization.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.MembershipStatus;
import vikoba.service.common.enums.MembershipType;

import java.time.LocalDate;


@Entity
@Table(
        name = "group_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_group_member",
                        columnNames = {"group_id", "member_id"}
                ),
                @UniqueConstraint(
                        name = "uk_membership_number",
                        columnNames = {"group_id", "membership_number"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "group_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_group_member_group")
    )
    private VikobaGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "member_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_group_member_member")
    )
    private Member member;

    @Column(
            name = "membership_number",
            nullable = false,
            length = 50
    )
    private String membershipNumber;

    @Column(name = "joined_date", nullable = false)
    private LocalDate joinedDate;

    @Column(name = "exit_date")
    private LocalDate exitDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_type", nullable = false, length = 30)
    @Builder.Default
    private MembershipType membershipType = MembershipType.ORDINARY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MembershipStatus status = MembershipStatus.ACTIVE;
}
