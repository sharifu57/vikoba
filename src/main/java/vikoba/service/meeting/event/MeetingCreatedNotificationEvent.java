package vikoba.service.meeting.event;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record MeetingCreatedNotificationEvent(
        Long meetingId,
        String groupName,
        String title,
        LocalDate meetingDate,
        LocalTime startTime,
        String meetingMode,
        String location,
        String meetingLink,
        List<Recipient> recipients) {

    public record Recipient(String phone, String name) {
    }
}
