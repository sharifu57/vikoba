package vikoba.service.meeting.entity;
import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.organization.entity.GroupMember;
@Entity @Table(name = "meeting_comments") @Getter @Setter @NoArgsConstructor
public class MeetingComment extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "group_member_id", nullable = false)
    private GroupMember groupMember;
    @Column(nullable = false, length = 2000) private String content;
}
