package vikoba.service.organization.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.MeetingFrequency;
import vikoba.service.common.enums.VikobaGroupStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vikoba_groups", uniqueConstraints = {
                @UniqueConstraint(name = "uk_group_organization_code", columnNames = { "organization_id", "code" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VikobaGroup extends BaseEntity {
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "organization_id", nullable = false, foreignKey = @ForeignKey(name = "fk_group_organization"))
        private Organization organization;

        @Column(nullable = false, length = 200)
        private String name;

        @Column(nullable = false, length = 50)
        private String code;

        @Column(name = "phone", length = 30)
        private String phone;

        @Column(name = "registration_number", length = 100)
        private String registrationNumber;

        @Column(columnDefinition = "TEXT")
        private String description;

        @Column(name = "formation_date")
        private LocalDate formationDate;

        @Enumerated(EnumType.STRING)
        @Column(name = "meeting_frequency", length = 30)
        private MeetingFrequency meetingFrequency;

        @Column(name = "meeting_day", length = 20)
        private String meetingDay;

        @Column(nullable = false, length = 10)
        @Builder.Default
        private String currency = "TZS";

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        @Builder.Default
        private VikobaGroupStatus status = VikobaGroupStatus.ACTIVE;

        @OneToMany(mappedBy = "group", fetch = FetchType.LAZY)
        @Builder.Default
        private List<GroupMember> members = new ArrayList<>();
}
