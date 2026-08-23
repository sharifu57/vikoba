package vikoba.service.meeting.dto;

import lombok.*;
import vikoba.service.common.enums.AttendanceStatus;
import vikoba.service.common.enums.MeetingStatus;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingAttendanceResponse {

    private Long id;

    private Long meetingId;

    private String meetingTitle;

    private LocalDate meetingDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private String location;

    private MeetingStatus meetingStatus;

    private AttendanceStatus attendanceStatus;

    private LocalTime arrivalTime;

    private String reason;
}
