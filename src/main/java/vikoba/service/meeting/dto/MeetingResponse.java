package vikoba.service.meeting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingResponse {
    private Long id;
    private Long groupId;
    private String title;
    private LocalDate meetingDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String location;
    private String status;
    private String agenda;
}
