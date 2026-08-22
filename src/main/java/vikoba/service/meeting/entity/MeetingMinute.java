package vikoba.service.meeting.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;

import java.time.LocalDateTime;


@Entity
@Table(name = "meeting_minutes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingMinute extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false, unique = true)
    private Meeting meeting;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
}
