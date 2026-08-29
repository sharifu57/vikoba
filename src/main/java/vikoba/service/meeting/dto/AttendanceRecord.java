package vikoba.service.meeting.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
public class AttendanceRecord {
    private Long groupMemberId;
    private String status; // PRESENT, ABSENT, LATE, EXCUSED
    private LocalTime arrivalTime;
    private String reason;
}
