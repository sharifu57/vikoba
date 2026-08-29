package vikoba.service.meeting.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.AttendanceStatus;
import vikoba.service.organization.entity.GroupMember;

import java.time.LocalTime;


@Entity
@Table(
        name = "meeting_attendance",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_meeting_member",
                        columnNames = {"meeting_id", "group_member_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingAttendance extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "group_member_id", nullable = false)
    private GroupMember groupMember;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(name = "arrival_time")
    private LocalTime arrivalTime;

    @Column(length = 255)
    private String reason;
}
