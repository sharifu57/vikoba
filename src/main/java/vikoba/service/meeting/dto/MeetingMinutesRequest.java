package vikoba.service.meeting.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MeetingMinutesRequest {
    private String content;
    private boolean approved;
}
