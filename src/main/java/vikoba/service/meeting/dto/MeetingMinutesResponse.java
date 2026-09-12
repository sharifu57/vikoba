package vikoba.service.meeting.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MeetingMinutesResponse {
    private Long id;
    private Long meetingId;
    private String content;
    private LocalDateTime approvedAt;
    private LocalDateTime updatedAt;
}
