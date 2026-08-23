package vikoba.service.meeting.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class CreateMeetingRequest {
    private String title;
    private LocalDate meetingDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String location;
    private String agenda;
}
