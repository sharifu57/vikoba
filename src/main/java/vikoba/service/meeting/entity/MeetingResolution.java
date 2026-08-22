package vikoba.service.meeting.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.ResolutionStatus;

import java.time.LocalDate;


@Entity
@Table(name = "meeting_resolutions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingResolution extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "resolution_date", nullable = false)
    private LocalDate resolutionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResolutionStatus status;
}
